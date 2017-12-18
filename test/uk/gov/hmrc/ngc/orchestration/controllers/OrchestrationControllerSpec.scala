/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.ngc.orchestration.controllers

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import play.api.Play.current
import play.api.http.{HeaderNames, MimeTypes}
import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import play.api.mvc
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsJson}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.msasync.repository.AsyncRepository
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, PreFlightCheckResponse}
import uk.gov.hmrc.ngc.orchestration.executors.ExecutorFactory
import uk.gov.hmrc.ngc.orchestration.services._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class OrchestrationControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {


  trait mocks {
    implicit val mockMFAIntegration: MFAIntegration = mock[MFAIntegration]
    implicit val mockGenericConnector: GenericConnector = mock[GenericConnector]
    implicit val mockAuditConnector: AuditConnector = mock[AuditConnector]
    implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
    implicit val executorFactory: ExecutorFactory = new ExecutorFactory(mockGenericConnector, mockAuditConnector, fakeApplication.configuration)
    lazy val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, executorFactory, mockGenericConnector,
      fakeApplication.configuration ,mockAuditConnector, mockAuthConnector, 200)
  }


  implicit val materializer: ActorMaterializer = ActorMaterializer()(ActorSystem())
  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val actorSystem: ActorSystem = Akka.system
  lazy val lifecycle: ApplicationLifecycle = current.injector.instanceOf[ApplicationLifecycle]
  lazy val reactiveMongo: ReactiveMongoComponent = current.injector.instanceOf[ReactiveMongoComponent]

  val mockLiveOrchestrationService: LiveOrchestrationService = mock[LiveOrchestrationService]

  val journeyId: String = UUID.randomUUID().toString
  val nino = "CS700100A"
  val requestHeaders = Seq(HeaderNames.CONTENT_TYPE → MimeTypes.JSON, HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json", HeaderNames.AUTHORIZATION → "Bearer 11111111")
  val startupRequestWithHeader: FakeRequest[AnyContentAsJson] = FakeRequest()
    .withHeaders(HeaderNames.CONTENT_TYPE → MimeTypes.JSON, HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json", HeaderNames.AUTHORIZATION → "Bearer 11111111")
    .withJsonBody(Json.parse("""{
                    |  "device": {
                    |    "osVersion": "10.3.3",
                    |    "os": "ios",
                    |    "appVersion": "4.9.0",
                    |    "model": "iPhone8,2"
                    |  },
                    |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                    |}""".stripMargin))

  val startupRequestWithoutHeaders: FakeRequest[AnyContentAsJson] = FakeRequest()
    .withJsonBody(Json.parse("""{
                               |  "device": {
                               |    "osVersion": "10.3.3",
                               |    "os": "ios",
                               |    "appVersion": "4.9.0",
                               |    "model": "iPhone8,2"
                               |  },
                               |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                               |}""".stripMargin))

  def mockServicePreFlightCall(response: PreFlightCheckResponse): OngoingStubbing[Future[PreFlightCheckResponse]] = {
    when(mockLiveOrchestrationService.preFlightCheck(any[PreFlightRequest](), any[Option[String]]())(any[HeaderCarrier]()))
      .thenReturn(Future.successful(response))
  }

  type GrantAccess = Option[String] ~ ConfidenceLevel
  def stubAuthorisationGrantAccess(response: GrantAccess)(implicit authConnector: AuthConnector): OngoingStubbing[Future[GrantAccess]] = {
    when(authConnector.authorise(any[Predicate](), any[Retrieval[GrantAccess]]())(any[HeaderCarrier](), any[ExecutionContext]()))
      .thenReturn(Future.successful(response))
  }

  "preFlightCheck live controller" should {
    "return the Pre-Flight Check Response successfully" in new mocks {

      val expectation = PreFlightCheckResponse(upgradeRequired = true, Accounts(Some(Nino("CS700100A")), None, routeToIV = false, routeToTwoFactor = false, journeyId, "some-cred-id", "Individual"))
      mockServicePreFlightCall(expectation)
      val versionBody: JsValue = Json.parse("""{"os":"android", "version":"1.0.1"}""")
      val versionRequest: FakeRequest[JsValue] = FakeRequest().withBody(versionBody).withHeaders(CONTENT_TYPE -> JSON, ACCEPT -> "application/vnd.hmrc.1.0+json")

      val controller = new TestLiveOrchestrationController(fakeApplication.configuration, mockAuditConnector, mockAuthConnector, mockLiveOrchestrationService, actorSystem, lifecycle, reactiveMongo, 10, 10, 200, 30000, "PreflightHappyPath")
      val result: mvc.Result = await(controller.preFlightCheck(Some(journeyId))(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))(Duration(10,TimeUnit.SECONDS))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.toJson(expectation)
      result.header.headers("Set-Cookie") contains "mdtpapi="
    }
  }

  "OrchestrationController.orchestrate" should {
    "return throttle status response when throttle limit has been hit" in {

      val controller: NativeAppsOrchestrationController = new NativeAppsOrchestrationController {
        override val service: OrchestrationService = mock[OrchestrationService]
        override val eventMax: Int = 10
        override val serviceMax: Int = 10
        override val maxAgeForSuccess: Int = 10000
        override val auditConnector: AuditConnector = mock[AuditConnector]
        override val confLevel: Int = 200
        override val repository: AsyncRepository = mock[AsyncRepository]
        override val app: String = "test"
        override def authConnector: AuthConnector = mock[AuthConnector]
        override def throttleLimit = 0
        override protected lazy val actorSystem = OrchestrationControllerSpec.this.actorSystem
        override protected lazy val lifecycle = OrchestrationControllerSpec.this.lifecycle

        override protected def appNameConfiguration = fakeApplication.configuration
      }
      val result = performOrchestrate(Json.stringify(Json.obj()), controller, Nino(nino))
      status(result) shouldBe 429
      jsonBodyOf(result) shouldBe TestData.statusThrottle
    }

    "return unauthorized when authority record does not contain a NINO" in new mocks {
      stubAuthorisationGrantAccess(Some("") and ConfidenceLevel.L50)
      val controller = new TestLiveOrchestrationController(fakeApplication.configuration, mockAuditConnector, mockAuthConnector, liveOrchestrationService, actorSystem, lifecycle, reactiveMongo, 10, 10, 200, 30000, "UnauthorisedNoNino")
      val response: mvc.Result = await(controller.orchestrate(Nino(nino), Some(journeyId))(startupRequestWithHeader))(Duration(10, TimeUnit.SECONDS))
      status(response) shouldBe 200
      jsonBodyOf(response) shouldBe TestData.pollResponse
      response.header.headers.get("Set-Cookie").head shouldNot be(empty)
      val pollRequestWithCookieHeader: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        .withHeaders( HeaderNames.CONTENT_TYPE → MimeTypes.JSON,
          HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json",
          HeaderNames.AUTHORIZATION → "Bearer 11111111",
          HeaderNames.COOKIE → response.header.headers("Set-Cookie"))
      val pollResponse: mvc.Result = Eventually.eventually {
        await(controller.poll(Nino(nino), Some(journeyId))(pollRequestWithCookieHeader))(Duration(10, TimeUnit.SECONDS))
      }
      status(pollResponse) shouldBe 401
      Json.stringify(jsonBodyOf(pollResponse)) shouldBe """{"code":"UNAUTHORIZED","message":"NINO does not exist on account"}"""
    }

    "return 401 result with json status detailing low CL on authority" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L50)
      val controller = new TestLiveOrchestrationController(fakeApplication.configuration, mockAuditConnector, mockAuthConnector, liveOrchestrationService, actorSystem, lifecycle, reactiveMongo, 10, 10, 200, 30000, "TestingLowCL")
      val response: mvc.Result = await(controller.orchestrate(Nino(nino), Some(journeyId))(startupRequestWithHeader))(Duration(10, TimeUnit.SECONDS))
      status(response) shouldBe 200
      jsonBodyOf(response) shouldBe TestData.pollResponse
      response.header.headers.get("Set-Cookie").head shouldNot be(empty)
      val pollRequestWithCookieHeader: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        .withHeaders( HeaderNames.CONTENT_TYPE → MimeTypes.JSON,
                      HeaderNames.ACCEPT → "application/vnd.hmrc.1.0+json",
                      HeaderNames.AUTHORIZATION → "Bearer 11111111",
                      HeaderNames.COOKIE → response.header.headers("Set-Cookie"))
      val pollResponse: mvc.Result = Eventually.eventually {
        await(controller.poll(Nino(nino), Some(journeyId))(pollRequestWithCookieHeader))(Duration(10, TimeUnit.SECONDS))
      }
      status(pollResponse) shouldBe 401
      Json.stringify(jsonBodyOf(pollResponse)) shouldBe """{"code":"LOW_CONFIDENCE_LEVEL","message":"Confidence Level on account does not allow access"}"""
    }

    "return status code 406 when the headers are invalid" in new mocks {
      val controller = new LiveOrchestrationController(fakeApplication.configuration, mockAuditConnector, mockAuthConnector, mockLiveOrchestrationService, actorSystem, lifecycle, reactiveMongo, 10, 10, 200, 30000)
      val response: mvc.Result = await(controller.orchestrate(Nino(nino), Some(journeyId))(startupRequestWithoutHeaders))(Duration(10, TimeUnit.SECONDS))
      status(response) shouldBe 406
      Json.stringify(jsonBodyOf(response)) shouldBe """{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}"""
    }

  }

  "sandbox controller " should {

    "return the PreFlightCheckResponse response from a static resource" in new mocks {
      val versionBody: JsValue = Json.parse("""{"os":"android", "version":"1.0.1"}""")
      val versionRequestWithAuth: FakeRequest[JsValue] = FakeRequest().withBody(versionBody).withSession(
          "AuthToken" -> "Some Header"
        ).withHeaders(CONTENT_TYPE -> JSON, ACCEPT -> "application/vnd.hmrc.1.0+json", AUTHORIZATION -> "Bearer 123456789", "X-MOBILE-USER-ID" → "404893573708")
      val sandboxOrchestrationService = new SandboxOrchestrationService(mockGenericConnector, executorFactory)
      val controller = new SandboxOrchestrationControllerImpl(fakeApplication.configuration, mockAuditConnector, mockAuthConnector, sandboxOrchestrationService, actorSystem, lifecycle, 10, 10, 200)
      val result: mvc.Result = await(controller.preFlightCheck(Some(journeyId))(versionRequestWithAuth))
      status(result) shouldBe 200
      val journeyIdRetrieve: String = (contentAsJson(result) \ "accounts" \ "journeyId").as[String]
      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = false, journeyIdRetrieve, "someCredId", "Individual")))
    }

    "return startup response from a static resource" in new mocks {
      val requestWithAuth: FakeRequest[AnyContentAsJson] = FakeRequest().withJsonBody(Json.obj()).withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(CONTENT_TYPE -> JSON, ACCEPT -> "application/vnd.hmrc.1.0+json", AUTHORIZATION -> "Bearer 123456789", "X-MOBILE-USER-ID" → "404893573708")
      val sandboxOrchestrationService = new SandboxOrchestrationService(mockGenericConnector, executorFactory)
      val controller = new SandboxOrchestrationControllerImpl(fakeApplication.configuration, mockAuditConnector, mockAuthConnector, sandboxOrchestrationService, actorSystem, lifecycle, 10, 10, 200)
      val result: mvc.Result = await(controller.orchestrate(Nino(nino),Some(journeyId))(requestWithAuth))(Duration(10, TimeUnit.SECONDS))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe TestData.sandboxStartupResponse
    }

    "return poll response from a static resource" in new mocks {
      val currentTime: DateTime = new LocalDate().toDateTimeAtStartOfDay
      val requestWithAuth: FakeRequest[AnyContentAsJson] = FakeRequest().withJsonBody(Json.obj()).withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(CONTENT_TYPE -> JSON, ACCEPT -> "application/vnd.hmrc.1.0+json", AUTHORIZATION -> "Bearer 123456789", "X-MOBILE-USER-ID" → "404893573708")
      val sandboxOrchestrationService = new SandboxOrchestrationService(mockGenericConnector, executorFactory)
      val controller = new SandboxOrchestrationControllerImpl(fakeApplication.configuration, mockAuditConnector, mockAuthConnector, sandboxOrchestrationService, actorSystem, lifecycle, 10, 10, 200)
      val result: mvc.Result = await(controller.poll(Nino(nino))(requestWithAuth))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(TestData.sandboxPollResponse
        .replaceAll("previousDate1", currentTime.minusWeeks(2).getMillis.toString)
        .replaceAll("previousDate2", currentTime.minusWeeks(1).getMillis.toString)
        .replaceAll("previousDate3", currentTime.getMillis.toString)
        .replaceAll("date1", currentTime.plusWeeks(1).getMillis.toString)
        .replaceAll("date2", currentTime.plusWeeks(2).getMillis.toString)
        .replaceAll("date3", currentTime.plusWeeks(3).getMillis.toString)
        .replaceAll("date4", currentTime.plusWeeks(4).getMillis.toString)
        .replaceAll("date5", currentTime.plusWeeks(5).getMillis.toString)
        .replaceAll("date6", currentTime.plusWeeks(6).getMillis.toString)
        .replaceAll("date7", currentTime.plusWeeks(7).getMillis.toString)
        .replaceAll("date8", currentTime.plusWeeks(8).getMillis.toString))

      result.header.headers.get("Cache-Control") shouldBe Some("max-age=14400")
    }
  }


  def performOrchestrate(inputBody: String, controller: NativeAppsOrchestrationController, nino: Nino, journeyId: Option[String] = None): mvc.Result = {
    val token = "Bearer 123456789"
    val authToken = "AuthToken" -> token
    val authHeader = AUTHORIZATION -> token
    val body: JsValue = Json.parse(inputBody)
    val requestWithSessionKeyAndId = FakeRequest()
      .withSession(
        authToken
      ).withHeaders(
      ACCEPT -> "application/vnd.hmrc.1.0+json",
      authHeader
    ).withJsonBody(body)
    await(controller.orchestrate(nino, journeyId).apply(requestWithSessionKeyAndId))
  }
}