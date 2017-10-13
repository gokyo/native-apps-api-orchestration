package controllers

import play.api.http.{HeaderNames, MimeTypes}
import stubs.AuthStub.requestIsAuthenticated
import stubs.DataStreamStub._
import stubs.GenericStub._
import stubs.ServiceLocatorStub.registrationWillSucceed
import utils.{BaseISpec, Resource}

class LiveOrchestrationControllerISpec extends BaseISpec {

  private val headerThatSucceeds = Seq(HeaderNames.CONTENT_TYPE  → MimeTypes.JSON,
                                       HeaderNames.ACCEPT        → "application/vnd.hmrc.1.0+json",
                                       HeaderNames.AUTHORIZATION → "Bearer 11111111")

  private val journeyId = "f7a5d556-9f34-47cb-9d84-7e904f2fe704"

  def withJourneyParam(journeyId: String) = s"?journeyId=$journeyId"

  "POST of /native-app/preflight-check" should {
    "return a 200 when the user is authenticated" in {
      writeAuditSucceeds()
      registrationWillSucceed()
      requestIsAuthenticated("CS700100A")
      versionCheckPassed()
      val postRequest = """{
                          |    "os": "ios",
                          |    "version" : "0.1.0",
                          |    "mfa":{
                          |  	    "operation":"start"
                          |    }
                          |}""".stripMargin
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe true
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe "CS700100A"
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }
  }



  "POST of /native-app/startup" should {
    "return a 200 when the user is authenticated" in {
      writeAuditSucceeds()
      requestIsAuthenticated("CS700100A")
      val postRequest = """{
                          |  "device": {
                          |    "osVersion": "10.3.3",
                          |    "os": "ios",
                          |    "appVersion": "4.9.0",
                          |    "model": "iPhone8,2"
                          |  },
                          |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                          |}""".stripMargin
      val response = await(new Resource(s"/native-app/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
    }
  }
}
