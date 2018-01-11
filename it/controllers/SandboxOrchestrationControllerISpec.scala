package controllers

import org.joda.time.LocalDate
import org.scalatest.concurrent.Eventually._
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.Json
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.http.HttpResponse
import utils.{BaseISpec, Resource}

class SandboxOrchestrationControllerISpec extends BaseISpec {

  private val headerThatSucceeds = Seq(HeaderNames.CONTENT_TYPE  → MimeTypes.JSON,
                                       HeaderNames.ACCEPT        → "application/vnd.hmrc.1.0+json",
                                       HeaderNames.AUTHORIZATION → "Bearer 11111111",
                                      "X-MOBILE-USER-ID" → "208606423740")

  private val journeyId = "f7a5d556-9f34-47cb-9d84-7e904f2fe704"

  def withJourneyParam(journeyId: String) = s"journeyId=$journeyId"
  def withCookieHeader(response: HttpResponse) = {
    Seq(HeaderNames.COOKIE → response.allHeaders.get("Set-Cookie").getOrElse(throw new Exception("NO COOKIE FOUND")).head)
  }

  "POST of /native-app/preflight-check with X-MOBILE-USER-ID header" should {
    "successfully switch to the sandbox preflight" in {
      val nino = "CS700100A"
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/preflight-check?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      (response.json \ "upgradeRequired").as[Boolean] shouldBe false
      (response.json \ "accounts" \ "nino" ).as[String] shouldBe nino
      (response.json \ "accounts" \ "routeToIV" ).as[Boolean] shouldBe false
      (response.json \ "accounts" \ "routeToTwoFactor" ).as[Boolean] shouldBe false
    }
  }

  "POST of /native-app/:nino/startup with X-MOBILE-USER-ID header" should {
    "successfully switch to sandbox startup with poll mimicking the live controllers asynchronous response of the startup call" in {
      val nino = "CS700100A"
      val currentTime = (new LocalDate()).toDateTimeAtStartOfDay
      val postRequest = """{"os":"ios","version":"0.1.0","mfa":{"operation":"start"}}"""
      val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(postRequest, headerThatSucceeds))
      response.status shouldBe 200
      response.body shouldBe """{"status":{"code":"poll"}}"""
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)
      val pollResponse = eventually {
        await(new Resource(s"/native-app/$nino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      }
      pollResponse.status shouldBe 200
      (pollResponse.json \\ "taxSummary") shouldNot(be(empty))
    }

    "successfully switch to sandbox startup with poll mimicking the live controllers asynchronous response of the startup call " +
    "returning sandbox data for tax credit claimant-details request" in new FileResource {
      val nino = "CS700100A"
      val request = """{"serviceRequest":[{"name": "claimant-details"}]}"""

      val expectedResponseData = Json.parse(findResource("/resources/generic/poll/claimant-details.json").get)
      val response = await(new Resource(s"/native-app/$nino/startup?${withJourneyParam(journeyId)}", port).postAsJsonWithHeader(request, headerThatSucceeds))
      response.status shouldBe 200
      response.body shouldBe """{"status":{"code":"poll"}}"""
      response.allHeaders("Set-Cookie").head shouldNot be(empty)
      val headerWithCookie = headerThatSucceeds ++ withCookieHeader(response)
      val pollResponse = eventually {
        await(new Resource(s"/native-app/$nino/poll?${withJourneyParam(journeyId)}", port).getWithHeaders(headerWithCookie))
      }

      pollResponse.status shouldBe 200
      val serviceResponse = (pollResponse.json \ "OrchestrationResponse" \ "serviceResponse").get.head
      (serviceResponse \ "name").as[String] shouldBe "claimant-details"
      (serviceResponse \ "responseData").get shouldBe expectedResponseData
      (serviceResponse \ "failure").as[Boolean] shouldBe false
      (pollResponse.json \ "status").get shouldBe Json.obj("code" → "complete")
    }
  }

}
