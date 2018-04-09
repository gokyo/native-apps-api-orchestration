package controllers

import java.util.UUID.randomUUID

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.Eventually._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json.parse
import play.api.libs.json.{JsArray, JsObject, Json}
import stubs.AuthStub._
import stubs.CustomerProfileStub._
import stubs.DataStreamStub._
import stubs.PersonalIncomeStub._
import stubs.PushRegistrationStub._
import stubs.ServiceLocatorStub.registrationWillSucceed
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.time.TaxYear
import utils.{BaseISpec, Resource}

class LiveOrchestrationControllerISpec extends BaseISpec with FileResource{

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
      "widget.help_to_save.required_data" → "workingTaxCredit"
    )

  protected def withJourneyParam(journeyId: String) = s"journeyId=$journeyId"

  protected def withCookieHeader(response: HttpResponse): Seq[(String, String)] = {
    Seq(HeaderNames.COOKIE → response.allHeaders.getOrElse("Set-Cookie", throw new Exception("NO COOKIE FOUND")).head)
  }
  protected def gimmeUniqueToken(): String = randomUUID().toString

  private val successfulStartUpResponseBody = """{"status":{"code":"poll"}}"""

  protected def pollForResponse(nino: String, headerWithCookie: Seq[(String, String)]): HttpResponse = {
    eventually {
      val result = await(new Resource(s"/native-app/$nino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      result.body should not be successfulStartUpResponseBody
      result
    }
  }

  // until the apps are changed to remove "mfa" then some of the following tests include it as input but it should be ignored
  // also routeToTwoFactor is still specified and should always be false
  "POST of /native-app/preflight-check" should {
    "succeed for an authenticated user with 'confidence level' of 200" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorised(nino)
      versionCheckSucceeds(upgrade = true)
      val postRequest = """{"os":"ios","version":"0.1.0"}"""
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
      authorised(nino)
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
      authorised(nino)
      versionCheckUpgradeRequiredFails(400)
      val postRequest = """{"os":"ios","version":"0.1.0"}"""
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
      authorised(nino)
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
      val postRequest = """{"os":"ios","version":"0.1.0"}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 401
    }

    "generate a unique journeyId if no journeyId is provided" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      registrationWillSucceed()
      authorised(nino)
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
      authorisedButNotGovernmentGateway(nino)
      versionCheckSucceeds(upgrade = true)
      val postRequest = """{"os":"ios","version":"0.1.0"}"""
      val response = await(new Resource("/native-app/preflight-check", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 401
    }
  }

  "POST of /native-app/:nino/startup with GET of /native-app/:nino/poll" should {
    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning 401 when the Tax Summary response NINO does match the authority NINO" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      authorised(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson("AB123456D"))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
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
      response.body shouldBe successfulStartUpResponseBody
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
      authorised(nino)
      taxSummarySucceeds(nino, currentYear, taxSummaryJson(nino))
      taxCreditSummarySucceeds(nino, taxCreditSummaryJson)
      taxCreditsDecisionSucceeds(nino)
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
      response.body shouldBe successfulStartUpResponseBody
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
      authorisedWithNoNino()

      val pollResponse = pollForResponse(nino, headerThatSucceeds)
      pollResponse.status shouldBe 401
      (pollResponse.json \ "taxSummary").asOpt[JsObject] shouldBe None
      (pollResponse.json \ "taxCreditSummary").asOpt[JsObject] shouldBe None
    }
  }

  "POST of /native-app/:nino/startup with GET of /native-app/:nino/poll" should {
    "return a http 200 status with a body status code 'poll' for an authenticated user, " +
      "with poll asynchronously returning the orchestrated response of the startup call" in {
      val nino = "CS700100A"
      writeAuditSucceeds()
      authorised(nino)
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
      response.body shouldBe successfulStartUpResponseBody
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = pollForResponse(nino, headerWithCookie)
      pollResponse.status shouldBe 200
      (pollResponse.json \ "taxSummary").as[JsObject] shouldBe Json.parse(taxSummaryJson(nino))
      (pollResponse.json \ "taxCreditSummary").as[JsObject] shouldBe Json.parse(taxCreditSummaryJson)
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
      authorised(nino)
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
      response.body shouldBe successfulStartUpResponseBody
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)

      val pollResponse = pollForResponse(nino, headerWithCookie)
      pollResponse.status shouldBe 200
      (pollResponse.json \ "taxSummary").as[JsObject] shouldBe Json.parse(taxSummaryJson(nino))
      (pollResponse.json \ "taxCreditSummary").as[JsObject] shouldBe Json.obj()
      (pollResponse.json \ "taxCreditRenewals"\ "submissionsState").as[String] shouldBe "open"
      Json.stringify((pollResponse.json \\ "status").head) shouldBe """{"code":"complete"}"""

      pollResponse.allHeaders("Cache-Control").head shouldBe "max-age=14400"

      verify(0, getRequestedFor(urlMatching("/multi-factor-authentication")))
    }
  }

  "POST of /native-app/:nino/poll? for claimantDetails" should {
    val nino = "CS700100A"
    val barcodeReference = "200000000000013"

    def successfulStartup: Seq[(String, String)] = {
      val targetResource = new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port)
      val requestJson = """{"serviceRequest":[{"name": "claimant-details"}]}"""
      val startupResponse = await(targetResource.postAsJsonWithHeader(requestJson, headerThatSucceeds))

      startupResponse.status shouldBe 200
      startupResponse.body shouldBe successfulStartUpResponseBody
      startupResponse.allHeaders("Set-Cookie").head shouldNot be(empty)

      headerThatSucceeds ++ withCookieHeader(startupResponse)
    }

    "return claimant details with renewalFormType populated where barcode reference is not '000000000000000'" in {
      writeAuditSucceeds()
      authorised(nino)
      claimantDetailsAreFound(nino, barcodeReference, journeyId)

      val pollResponse = pollForResponse(nino, successfulStartup)
      pollResponse.status shouldBe 200
      pollResponse.json shouldBe parse(findResource("/resources/generic/tax-credit-claimant-details-response.json").get)
    }

    "return failure flag == true when there is an error" in {
      writeAuditSucceeds()
      authorised(nino)
      claimantDetailsFails(nino)

      val pollResponse = pollForResponse(nino, successfulStartup)
      pollResponse.status shouldBe 200

      val serviceResponse = (pollResponse.json \ "OrchestrationResponse" \ "serviceResponse").get.head
      (serviceResponse \ "name").as[String] shouldBe "claimant-details"
      (serviceResponse \ "failure").as[Boolean] shouldBe true
      (pollResponse.json \ "status").get shouldBe Json.obj("code" → "complete")
    }
  }
}