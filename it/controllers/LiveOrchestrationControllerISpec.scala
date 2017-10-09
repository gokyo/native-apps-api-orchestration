package controllers

import org.scalatestplus.play.OneServerPerSuite
import stubs.AuthStub
import uk.gov.hmrc.play.test.UnitSpec
import utils.{Resource, WireMockSupport}

trait LiveOrchestrationControllerISpec extends UnitSpec with OneServerPerSuite with WireMockSupport with AuthStub {

  "POST of /native-app/preflight-check" should {
    "return a 200 when the user is authenticated" in {
      requestIsAuthenticated("CS700100A")
      val postRequest = """{
                          |    "os": "ios",
                          |    "version" : "0.1.0",
                          |    "mfa":{
                          |  	    "operation":"start"
                          |    }
                          |}""".stripMargin
      val response = await(new Resource("/native-app/preflight-check", port).postAsJson(postRequest))
      response.status shouldBe 200
    }

}
