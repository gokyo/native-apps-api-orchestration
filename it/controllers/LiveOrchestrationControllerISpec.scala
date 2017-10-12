package controllers

import play.api.http.{HeaderNames, MimeTypes}
import stubs.AuthStub.requestIsAuthenticated
import stubs.DataStreamStub._
import stubs.GenericStub._
import stubs.ServiceLocatorStub.registrationWillSucceed
import utils.{BaseISpec, Resource}

class LiveOrchestrationControllerISpec extends BaseISpec {

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
      val headers = Seq(HeaderNames.CONTENT_TYPE  → MimeTypes.JSON,
                        HeaderNames.ACCEPT        → "application/vnd.hmrc.1.0+json",
                        HeaderNames.AUTHORIZATION → "Bearer 11111111")
      val response = await(new Resource("/native-app/preflight-check", port).postAsJsonWithHeader(postRequest, headers))
      response.status shouldBe 200
      response.body shouldBe """{"upgradeRequired":true,"accounts":{"nino":"CS700100A","routeToIV":false,"routeToTwoFactor":false,"journeyId":"f7a5d556-9f34-47cb-9d84-7e904f2fe704"}}"""
    }
  }
}
