package stubs

import org.joda.time.DateTime
import play.api.libs.json.Json
import stubs.StubShortcuts._
import uk.gov.hmrc.ngc.orchestration.services.JourneyResponse

object MFAIntegrationStub {

  val startResponse =
    """
      |{
      |  "_links": {
      |    "browser": {
      |      "href": "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
      |    },
      |    "self": {
      |      "href": "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
      |    }
      |  }
      |}
    """.stripMargin

  def routeToTwoFactor = {
    stubPostSuccess("/multi-factor-authentication/authenticatedJourney", startResponse)
  }

  def mfaFailure(status: Int) : Unit = {
    val error = """{"error":"Controlled Explosion"}"""
    stubPostFailure("/multi-factor-authentication/authenticatedJourney", status, error)
  }

  def mfaOutcomeStatus(status: String) : Unit = {
    val response = Json.stringify(Json.toJson(JourneyResponse("","",None,"","","",false,None,None,status,DateTime.now)))
    stubGetSuccess("/multi-factor-authentication/journey/58d93f54280000da005d388b", response)
    routeToTwoFactor
  }
}
