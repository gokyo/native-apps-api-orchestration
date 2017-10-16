package controllers

import org.scalatest.concurrent.Eventually._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.Json
import stubs.AuthStub.requestIsAuthenticated
import stubs.DataStreamStub._
import stubs.GenericStub._
import stubs.ServiceLocatorStub.registrationWillSucceed
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.time.TaxYear
import utils.{BaseISpec, Resource}

class LiveOrchestrationControllerISpec extends BaseISpec {

  private val headerThatSucceeds = Seq(HeaderNames.CONTENT_TYPE  → MimeTypes.JSON,
                                       HeaderNames.ACCEPT        → "application/vnd.hmrc.1.0+json",
                                       HeaderNames.AUTHORIZATION → "Bearer 11111111")

  private val journeyId = "f7a5d556-9f34-47cb-9d84-7e904f2fe704"
  private val currentYear = TaxYear.current.currentYear toString

  def withJourneyParam(journeyId: String) = s"?journeyId=$journeyId"
  def withCookieHeader(response: HttpResponse) = {
    Seq(HeaderNames.COOKIE → response.allHeaders.get("Set-Cookie").getOrElse(throw new Exception("NO COOKIE FOUND")).head)
  }

  "POST of /native-app/preflight-check" should {
    "return a http 200 status for an authenticated user with 'confidence level' of 200 and 'strong' cred strength" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      requestIsAuthenticated(nino, 200, "strong")
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
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }
  }

  "POST of /native-app/:nino/startup with GET of /native-app/:nino/poll" should {
    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning the orchestrated response of the startup call" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      requestIsAuthenticated(nino)
      taxSummarySucceeds(nino, currentYear)
      taxCreditSummarySucceeds(nino)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      val postRequest = """{
                          |  "device": {
                          |    "osVersion": "10.3.3",
                          |    "os": "ios",
                          |    "appVersion": "4.9.0",
                          |    "model": "iPhone8,2"
                          |  },
                          |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                          |}""".stripMargin
      val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      response.body shouldBe """{"status":{"code":"poll"}}"""
      response.allHeaders.get("Set-Cookie").get.head shouldNot(be(empty))
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = eventually {
        await(new Resource(s"/native-app/$nino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      }
      pollResponse.status shouldBe 200
      (pollResponse.json \\ "taxSummary") shouldNot (be(None))
      (pollResponse.json \\ "taxCreditSummary") shouldNot (be(None))
      (pollResponse.json \\ "state") shouldNot (be(None))
      (pollResponse.json \\ "campaigns") shouldNot (be(None))
      Json.stringify((pollResponse.json \\ "status").head) shouldBe """{"code":"complete"}"""
    }
  }
}
