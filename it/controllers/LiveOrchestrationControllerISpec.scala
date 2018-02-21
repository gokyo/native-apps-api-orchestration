package controllers

import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.Eventually._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import stubs.AuthStub._
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

trait BaseLiveOrchestrationControllerISpec extends BaseISpec {

  protected val headerThatSucceeds = Seq(HeaderNames.CONTENT_TYPE  → MimeTypes.JSON,
                                       HeaderNames.ACCEPT        → "application/vnd.hmrc.1.0+json",
                                       HeaderNames.AUTHORIZATION → "Bearer 11111111")

  protected val journeyId = "f7a5d556-9f34-47cb-9d84-7e904f2fe704"
  protected val currentYear = TaxYear.current.currentYear.toString

  override protected def appBuilder: GuiceApplicationBuilder =
    super.appBuilder.configure(
      "widget.help_to_save.enabled" → true,
      "widget.help_to_save.min_views" → 5,
      "widget.help_to_save.dismiss_days" → 15,
      "widget.help_to_save.required_data" → "workingTaxCredit",
      "routeToTwoFactorAlwaysFalse" -> routeToTwoFactorAlwaysFalse
    )

  def routeToTwoFactorAlwaysFalse: Boolean = ???

  protected def withJourneyParam(journeyId: String) = s"journeyId=$journeyId"

  protected def withCookieHeader(response: HttpResponse): Seq[(String, String)] = {
    Seq(HeaderNames.COOKIE → response.allHeaders.getOrElse("Set-Cookie", throw new Exception("NO COOKIE FOUND")).head)
  }
  protected def gimmeUniqueToken(): String = randomUUID().toString
  protected def pollForResponse(nino: String, headerWithCookie: Seq[(String, String)]): HttpResponse = {
    eventually {
      val result = await(new Resource(s"/native-app/$nino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      result.body should not be """{"status":{"code":"poll"}}"""
      result
    }
  }

  "POST of /native-app/preflight-check" should {
    "succeed for an authenticated user with 'confidence level' of 200 and 'strong' cred strength" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedWithStrongCredentials(nino)
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
      authorisedWithStrongCredentials(nino)
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
      authorisedWithStrongCredentials(nino)
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
      authorisedWithStrongCredentials(nino)
      versionCheckUpgradeRequiredFails(500)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }

    "return 401 HTTP status code when calls to retrieve the auth account fails" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      notAuthorised()
      versionCheckSucceeds(upgrade = false)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 401
    }

    "generate a unique journeyId if no journeyId is provided" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedWithStrongCredentials(nino)
      versionCheckSucceeds(upgrade = true)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource("/native-app/preflight-check", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe true
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "journeyId" ).as[String].length > 0 shouldBe true
    }

    "return a 401 when the authProvider is not GovernmentGateway" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedWithStrongCredentialsAndNotGovernmentGateway(nino)
      versionCheckSucceeds(upgrade = true)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource("/native-app/preflight-check", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 401
    }

    "return bad request when the MFA operation supplied is invalid" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedWithStrongCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val invalidOperation = "BLAH"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$invalidOperation"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 400
    }
  }

  "POST of /native-app/:nino/startup with GET of /native-app/:nino/poll" should {
    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning 401 when the Tax Summary response NINO does match the authority NINO" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      authorisedWithStrongCredentials(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson("AB123456D"))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      authorisedWithStrongCredentials(nino)
      val postRequest = s"""{
                          |  "device": {
                          |    "osVersion": "10.3.3",
                          |    "os": "ios",
                          |    "appVersion": "4.9.0",
                          |    "model": "iPhone8,2"
                          |  },
                          |  "token": "${gimmeUniqueToken()}"
                          |}""".stripMargin
      val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      response.body shouldBe """{"status":{"code":"poll"}}"""
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie: Seq[(String, String)] = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = pollForResponse(nino, headerWithCookie)
      pollResponse.status shouldBe 401
    }

    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning 401 when the poll request NINO does not match the authority NINO" in {
      val nino = "CS700100A"
      val someOtherNino = "AB123456C"
      writeAuditSucceeds()
      authorisedWithStrongCredentials(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      authorisedWithStrongCredentials(nino)
      val postRequest = s"""{
                          |  "device": {
                          |    "osVersion": "10.3.3",
                          |    "os": "ios",
                          |    "appVersion": "4.9.0",
                          |    "model": "iPhone8,2"
                          |  },
                          |  "token": "${gimmeUniqueToken()}"
                          |}""".stripMargin
      val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      response.body shouldBe """{"status":{"code":"poll"}}"""
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = pollForResponse(someOtherNino, headerWithCookie)
      pollResponse.status shouldBe 401
    }
  }

  "GET of /native-app/:nino/poll" should {
    "return a http 401 status when /auth/authorise returns 401" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      notAuthorised()

      val pollResponse = pollForResponse(nino, headerThatSucceeds)
      pollResponse.status shouldBe 401
      (pollResponse.json \ "taxSummary").asOpt[JsObject] shouldBe None
      (pollResponse.json \ "taxCreditSummary").asOpt[JsObject] shouldBe None
    }

    // Is this too much of a corner case for integration testing?
    // We already have a unit test for this in AuthorisationSpec."Authorisation grantAccess" should "fail to return authority when no NINO exists"
    "return a http 401 status when /auth/authorise does not include a NINO in its response" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      authorisedWithStrongCredentialsAndNoNino()

      val pollResponse = pollForResponse(nino, headerThatSucceeds)
      pollResponse.status shouldBe 401
      (pollResponse.json \ "taxSummary").asOpt[JsObject] shouldBe None
      (pollResponse.json \ "taxCreditSummary").asOpt[JsObject] shouldBe None
    }
  }
}

class LiveOrchestrationControllerISpec extends BaseLiveOrchestrationControllerISpec {
  override def routeToTwoFactorAlwaysFalse = false

  "POST of /native-app/preflight-check with routeToTwoFactorAlwaysFalse = false " should {
    "call the MFA API URI and return routeToTwoFactor=true when cred-strength is not strong" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      routeToTwoFactor
      authorisedWithWeakCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired").as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino").as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV").as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor").as[Boolean] shouldBe true

      verify(1, postRequestedFor(urlMatching("/multi-factor-authentication/authenticatedJourney")))
    }

    "call the MFA API URI and return routeToTwoFactor=true when MFA API returns UNVERIFIED state" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("UNVERIFIED")
      authorisedWithWeakCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation", "apiURI": "/multi-factor-authentication/journey/58d93f54280000da005d388b"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe true

      verify(1, postRequestedFor(urlMatching("/multi-factor-authentication/authenticatedJourney")))
    }

    "return 500 response when the MFA service fails" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaFailure(500)
      authorisedWithWeakCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 500

      verify(1, postRequestedFor(urlMatching("/multi-factor-authentication/authenticatedJourney")))
    }

    "return 500 response when MFA returns unknown state" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("Some unknown state")
      authorisedWithWeakCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation", "apiURI": "/multi-factor-authentication/journey/58d93f54280000da005d388b"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))(Duration(40, TimeUnit.SECONDS))
      response.status shouldBe 500

      verify(1, getRequestedFor(urlMatching("/multi-factor-authentication/journey/58d93f54280000da005d388b")))
    }

    "return response with routeToTwoFactor=false when MFA returns NOT_REQUIRED state" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("NOT_REQUIRED")
      authorisedWithWeakCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation", "apiURI": "/multi-factor-authentication/journey/58d93f54280000da005d388b"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false

      verify(1, getRequestedFor(urlMatching("/multi-factor-authentication/journey/58d93f54280000da005d388b")))
    }

    "return response with routeToTwoFactor=false when MFA returns SKIPPED state" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("SKIPPED")
      authorisedWithWeakCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation", "apiURI": "/multi-factor-authentication/journey/58d93f54280000da005d388b"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false

      verify(1, getRequestedFor(urlMatching("/multi-factor-authentication/journey/58d93f54280000da005d388b")))
    }

    "return bad request when the apiURI is not included in the request" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      mfaOutcomeStatus("NOT_REQUIRED")
      authorisedWithWeakCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val operation = "outcome"
      val postRequest = s"""{"os":"ios","version":"0.1.0","mfa":{"operation":"$operation"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))(Duration(40, TimeUnit.SECONDS))
      response.status shouldBe 400
    }
  }

  "POST of /native-app/:nino/startup with GET of /native-app/:nino/poll" should {
    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning the orchestrated response of the startup call" in {
        val nino = "CS700100A"
        writeAuditSucceeds()
        authorisedWithStrongCredentials(nino)
        taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
        taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
        taxCreditsDecisionSucceeds(nino)
        taxCreditsSubmissionStateIsEnabled()
        pushRegistrationSucceeds()
        val postRequest =
          s"""{
             |  "device": {
             |    "osVersion": "10.3.3",
             |    "os": "ios",
             |    "appVersion": "4.9.0",
             |    "model": "iPhone8,2"
             |  },
             |  "token": "${gimmeUniqueToken()}"
             |}""".stripMargin
        val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
        response.status shouldBe 200
        response.body shouldBe """{"status":{"code":"poll"}}"""
        response.allHeaders("Set-Cookie").head shouldNot be(empty)
        val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

        val pollResponse = pollForResponse(nino, headerWithCookie)
        pollResponse.status shouldBe 200
        (pollResponse.json \ "taxSummary").as[JsObject] shouldBe Json.parse(taxSummaryJson(nino))
        (pollResponse.json \ "taxCreditSummary").as[JsObject] shouldBe Json.parse(taxCreditSummaryJson)
        (pollResponse.json \ "state" \ "enableRenewals").as[Boolean] shouldBe false
        (pollResponse.json \ "taxCreditRenewals" \ "submissionsState").as[String] shouldBe "open"
        (pollResponse.json \ "campaigns").as[JsArray] shouldBe Json.parse(
          """[{"campaignId": "HELP_TO_SAVE_1", "enabled": true, "minimumViews": 5, "dismissDays": 15, "requiredData": "workingTaxCredit"}]"""
        )
        Json.stringify((pollResponse.json \\ "status").head) shouldBe """{"code":"complete"}"""

        pollResponse.allHeaders("Cache-Control").head shouldBe "max-age=14400"
      }

    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning a taxCreditSummary attribute with no summary data when tax credit decision returns false" in {
        val nino = "CS700100A"
        writeAuditSucceeds()
        authorisedWithStrongCredentials(nino)
        taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
        taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
        taxCreditsDecisionSucceeds(nino, showData = false)
        taxCreditsSubmissionStateIsEnabled()
        pushRegistrationSucceeds()
        val postRequest = s"""{
                             |  "device": {
                             |    "osVersion": "10.3.3",
                             |    "os": "ios",
                             |    "appVersion": "4.9.0",
                             |    "model": "iPhone8,2"
                             |  },
                             |  "token": "${gimmeUniqueToken()}"
                             |}""".stripMargin
        val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
        response.status shouldBe 200
        response.body shouldBe """{"status":{"code":"poll"}}"""
        response.allHeaders("Set-Cookie").head shouldNot be(empty)
        val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

        val pollResponse = pollForResponse(nino, headerWithCookie)
        pollResponse.status shouldBe 200
        (pollResponse.json \ "taxSummary").as[JsObject] shouldBe Json.parse(taxSummaryJson(nino))
        (pollResponse.json \ "taxCreditSummary").as[JsObject] shouldBe Json.obj()
        (pollResponse.json \ "state"\ "enableRenewals").as[Boolean] shouldBe false
        (pollResponse.json \ "taxCreditRenewals"\ "submissionsState").as[String] shouldBe "open"
        Json.stringify((pollResponse.json \\ "status").head) shouldBe """{"code":"complete"}"""

        pollResponse.allHeaders("Cache-Control").head shouldBe "max-age=14400"
      }
    }
  }

class LiveOrchestrationControllerWithRouteToTwoFactorAlwaysFalseISpec extends BaseLiveOrchestrationControllerISpec {
  override def routeToTwoFactorAlwaysFalse = true

  "POST of /native-app/preflight-check with routeToTwoFactorAlwaysFalse = true " should {
    "return routeToTwoFactor=false when cred-strength is strong" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedWithStrongCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired").as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino").as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV").as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor").as[Boolean] shouldBe false

      verify(0, postRequestedFor(urlMatching("/multi-factor-authentication/authenticatedJourney")))
      verify(0, getRequestedFor(urlMatching("/multi-factor-authentication/journey/58d93f54280000da005d388b")))
    }

    "return routeToTwoFactor=false and not call MFA when cred-strength is not strong" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorisedWithWeakCredentials(nino)
      versionCheckSucceeds(upgrade = false)
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired").as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino").as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV").as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor").as[Boolean] shouldBe false

      verify(0, postRequestedFor(urlMatching("/multi-factor-authentication/authenticatedJourney")))
      verify(0, getRequestedFor(urlMatching("/multi-factor-authentication/journey/58d93f54280000da005d388b")))
    }
  }

  "POST of /native-app/:nino/startup with GET of /native-app/:nino/poll with weak credential strength" should {
    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning the orchestrated response of the startup call" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      authorisedWithNinoOnlyReturningWeakCrednetialStrength(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      val postRequest =
        s"""{
           |  "device": {
           |    "osVersion": "10.3.3",
           |    "os": "ios",
           |    "appVersion": "4.9.0",
           |    "model": "iPhone8,2"
           |  },
           |  "token": "${gimmeUniqueToken()}"
           |}""".stripMargin
      val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      response.body shouldBe """{"status":{"code":"poll"}}"""
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = pollForResponse(nino, headerWithCookie)
      pollResponse.status shouldBe 200
      (pollResponse.json \ "taxSummary").as[JsObject] shouldBe Json.parse(taxSummaryJson(nino))
      (pollResponse.json \ "taxCreditSummary").as[JsObject] shouldBe Json.parse(taxCreditSummaryJson)
      (pollResponse.json \ "state" \ "enableRenewals").as[Boolean] shouldBe false
      (pollResponse.json \ "taxCreditRenewals" \ "submissionsState").as[String] shouldBe "open"
      (pollResponse.json \ "campaigns").as[JsArray] shouldBe Json.parse(
        """[{"campaignId": "HELP_TO_SAVE_1", "enabled": true, "minimumViews": 5, "dismissDays": 15, "requiredData": "workingTaxCredit"}]"""
      )
      Json.stringify((pollResponse.json \\ "status").head) shouldBe """{"code":"complete"}"""

      pollResponse.allHeaders("Cache-Control").head shouldBe "max-age=14400"

      verify(0, getRequestedFor(urlMatching("/multi-factor-authentication")))
    }

    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning a taxCreditSummary attribute with no summary data when tax credit decision returns false" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      authorisedWithNinoOnlyReturningWeakCrednetialStrength(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino, showData = false)
      taxCreditsSubmissionStateIsEnabled()
      pushRegistrationSucceeds()
      val postRequest = s"""{
                           |  "device": {
                           |    "osVersion": "10.3.3",
                           |    "os": "ios",
                           |    "appVersion": "4.9.0",
                           |    "model": "iPhone8,2"
                           |  },
                           |  "token": "${gimmeUniqueToken()}"
                           |}""".stripMargin
      val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      response.body shouldBe """{"status":{"code":"poll"}}"""
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = pollForResponse(nino, headerWithCookie)
      pollResponse.status shouldBe 200
      (pollResponse.json \ "taxSummary").as[JsObject] shouldBe Json.parse(taxSummaryJson(nino))
      (pollResponse.json \ "taxCreditSummary").as[JsObject] shouldBe Json.obj()
      (pollResponse.json \ "state"\ "enableRenewals").as[Boolean] shouldBe false
      (pollResponse.json \ "taxCreditRenewals"\ "submissionsState").as[String] shouldBe "open"
      Json.stringify((pollResponse.json \\ "status").head) shouldBe """{"code":"complete"}"""

      pollResponse.allHeaders("Cache-Control").head shouldBe "max-age=14400"

      verify(0, getRequestedFor(urlMatching("/multi-factor-authentication")))
    }
  }

}