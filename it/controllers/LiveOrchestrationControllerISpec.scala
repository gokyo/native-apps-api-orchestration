package controllers

import java.util.concurrent.TimeUnit

import org.scalatest.concurrent.Eventually._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import stubs.AuthorisedFunctionStub._
import stubs.CustomerProfileStub._
import stubs.DataStreamStub._
import stubs.MFAIntegrationStub._
import stubs.PersonalIncomeStub._
import stubs.PushRegistrationStub._
import stubs.ServiceLocatorStub.registrationWillSucceed
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.time.TaxYear
import utils.{BaseISpec, Resource}

import scala.concurrent.duration.Duration

class LiveOrchestrationControllerISpec extends BaseISpec {

  private val headerThatSucceeds = Seq(HeaderNames.CONTENT_TYPE  → MimeTypes.JSON,
                                       HeaderNames.ACCEPT        → "application/vnd.hmrc.1.0+json",
                                       HeaderNames.AUTHORIZATION → "Bearer 11111111")
  private val headerWithoutAuthorization = headerThatSucceeds.filter{ case (name: String, _) ⇒ name != HeaderNames.AUTHORIZATION }

  private val journeyId = "f7a5d556-9f34-47cb-9d84-7e904f2fe704"
  private val currentYear = TaxYear.current.currentYear toString

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder.configure(
      "widget.help_to_save.enabled" -> true,
      "widget.help_to_save.min_views" -> 5,
      "widget.help_to_save.dismiss_days" -> 15,
      "widget.help_to_save.required_data" -> "workingTaxCredit"
    )

  def withJourneyParam(journeyId: String) = s"journeyId=$journeyId"
  def withCookieHeader(response: HttpResponse) = {
    Seq(HeaderNames.COOKIE → response.allHeaders.get("Set-Cookie").getOrElse(throw new Exception("NO COOKIE FOUND")).head)
  }

  "POST of /native-app/preflight-check" should {
    "succeed for an authenticated user with 'confidence level' of 200 and 'strong' cred strength" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedFunctionSucceeds(nino, 200, "strong")
      versionCheckSucceeds(upgrade = true)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe true
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }

    "succeed with upgradeRequired: false when version-check returns upgrade: false" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedFunctionSucceeds(nino, 200, "strong")
      versionCheckSucceeds(upgrade = false)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }

    "succeed and default to upgradeRequired: false when version-check fails with with 400 BAD REQUEST" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedFunctionSucceeds(nino, 200, "strong")
      versionCheckUpgradeRequiredFails(400)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }

    "succeed and default to upgradeRequired: false when version-check fails with with 500 SERVER ERROR" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedFunctionSucceeds(nino, 200, "strong")
      versionCheckUpgradeRequiredFails(500)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }


    //TODO replicate this scenario

//    "return 401 HTTP status code when calls to retrieve the auth account fails" in {
//      val nino = "CS700100A"
//      writeAuditSucceeds()
//      registrationWillSucceed()
//      authorisedFunctionSucceeds(nino)
//      versionCheckSucceeds(upgrade = false)
//      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
//      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
//      response.status shouldBe 401
//    }

    "call the MFA API URI and return routeToTwoFactor=true when cred-strength is not strong" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      routeToTwoFactor
      authorisedFunctionSucceeds(nino, credentialStrength = "weak")
      versionCheckSucceeds(upgrade = false)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe true
    }

    "return 500 response when the MFA service fails" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaFailure(500)
      authorisedFunctionSucceeds(nino, credentialStrength = "weak")
      versionCheckSucceeds(upgrade = false)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 500
    }

    "return bad request when the MFA operation supplied is invalid" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedFunctionSucceeds(nino)
      versionCheckSucceeds(upgrade = false)
      val invalidOperation = "BLAH"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$invalidOperation"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 400
    }

    "call the MFA API URI and return routeToTwoFactor=true when MFA API returns UNVERIFIED state" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("UNVERIFIED")
      authorisedFunctionSucceeds(nino, credentialStrength = "weak")
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation", "apiURI": "/multi-factor-authentication/journey/58d93f54280000da005d388b"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe true
    }

    "return response with routeToTwoFactor=false when MFA returns NOT_REQUIRED state" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("NOT_REQUIRED")
      authorisedFunctionSucceeds(nino, credentialStrength = "weak")
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation", "apiURI": "/multi-factor-authentication/journey/58d93f54280000da005d388b"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }

    "return response with routeToTwoFactor=false when MFA returns SKIPPED state" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("SKIPPED")
      authorisedFunctionSucceeds(nino, credentialStrength = "weak")
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation", "apiURI": "/multi-factor-authentication/journey/58d93f54280000da005d388b"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }

    "return bad request when the apiURI is not included in the request" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("NOT_REQUIRED")
      authorisedFunctionSucceeds(nino, credentialStrength = "weak")
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))(Duration(40, TimeUnit.SECONDS))
      response.status shouldBe 400
    }

    "return 500 response when MFA returns unknown state" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("Some unknown state")
      authorisedFunctionSucceeds(nino, credentialStrength = "weak")
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation", "apiURI": "/multi-factor-authentication/journey/58d93f54280000da005d388b"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))(Duration(40, TimeUnit.SECONDS))
      response.status shouldBe 500
    }
  }

  "POST of /native-app/:nino/startup with GET of /native-app/:nino/poll" should {
    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning the orchestrated response of the startup call" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      authorisedFunctionSucceeds(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      authorisedFunctionGrantAccessSucceeds(nino)
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
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = eventually {
        await(new Resource(s"/native-app/$nino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      }
      pollResponse.status shouldBe 200
      (pollResponse.json \ "taxSummary").as[JsObject] shouldBe Json.parse(taxSummaryJson(nino))
      (pollResponse.json \ "taxCreditSummary").as[JsObject] shouldBe Json.parse(taxCreditSummaryJson)
      (pollResponse.json \ "state" \ "enableRenewals").as[Boolean] shouldBe true
      (pollResponse.json \ "campaigns").as[JsArray] shouldBe Json.parse(
        """[{"campaignId": "HELP_TO_SAVE_1", "enabled": true, "minimumViews": 5, "dismissDays": 15, "requiredData": "workingTaxCredit"}]"""
      )
      Json.stringify((pollResponse.json \\ "status").head) shouldBe """{"code":"complete"}"""
    }

    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning 401 when the Tax Summary response NINO does match the authority NINO" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      authorisedFunctionSucceeds(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson("AB123456D"))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      authorisedFunctionGrantAccessSucceeds(nino)
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
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = eventually {
        await(new Resource(s"/native-app/$nino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      }
      pollResponse.status shouldBe 401
    }

    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning 401 when the poll request NINO does not match the authority NINO" in {
      val nino = "CS700100A"
      val someOtherNino = "AB123456C"
      writeAuditSucceeds()
      authorisedFunctionSucceeds(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      authorisedFunctionGrantAccessSucceeds(nino)
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
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = eventually {
        await(new Resource(s"/native-app/$someOtherNino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      }
      pollResponse.status shouldBe 401
    }

    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning a taxCreditSummary attribute with no summary data when tax credit decision returns false" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      authorisedFunctionSucceeds(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino, showData = false)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      authorisedFunctionGrantAccessSucceeds(nino)
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
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = eventually {
        await(new Resource(s"/native-app/$nino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      }
      pollResponse.status shouldBe 200
      (pollResponse.json \ "taxSummary").as[JsObject] shouldBe Json.parse(taxSummaryJson(nino))
      (pollResponse.json \ "taxCreditSummary").as[JsObject] shouldBe Json.obj()
      (pollResponse.json \ "state" \ "enableRenewals").as[Boolean] shouldBe true
      Json.stringify((pollResponse.json \\ "status").head) shouldBe """{"code":"complete"}"""
    }
  }
}
