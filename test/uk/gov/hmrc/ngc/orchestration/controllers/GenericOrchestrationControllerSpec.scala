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

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.controllers.{LiveOrchestrationController, TestLiveOrchestrationController}
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, OrchestrationServiceRequest}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class GenericOrchestrationControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar with BeforeAndAfterAll with FileResource {

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  val token = "Bearer 123456789"

  val pollResponse: JsValue = Json.obj("status" -> Json.parse("""{"code":"poll"}"""))

  "LiveOrchestrationController call triggering generic orchestration implementation" should {

    "fail with a 406 for a request without an Accept header" in {

      val application = new GuiceApplicationBuilder()
        .configure("metrics.enabled" → false)
        .build()

      val controller = application.injector.instanceOf[LiveOrchestrationController]

      val request: JsValue = Json.parse(findResource(s"/resources/generic/version-check-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val result = await(controller.orchestrate(Nino("AB123456C"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 406
    }

    "return bad request when the service name supplied is unknown" in {

      val request: JsValue = Json.parse(findResource(s"/resources/generic/invalid-service-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val application = new GuiceApplicationBuilder()
        .configure("metrics.enabled" → false)
        .build()

      val controller = application.injector.instanceOf[LiveOrchestrationController]

      val result = await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 400
    }

    "should fail to execute if the number of services exceeds the max service config" in {

      val request: JsValue = Json.parse(findResource(s"/resources/generic/max-service-calls-exceeded-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val application = new GuiceApplicationBuilder()
        .configure("supported.generic.service.version-check.on" → true)
        .configure("supported.generic.service.max" → 2)
        .configure("metrics.enabled" → false)
        .build()

      val controller = application.injector.instanceOf[LiveOrchestrationController]
      val result = await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 400
    }

    "should fail to execute if the number of events exceeds the max event config" in {

      val request: JsValue = Json.parse(findResource(s"/resources/generic/max-event-calls-exceeded-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val application = new GuiceApplicationBuilder()
        .configure("supported.generic.event.ngc-audit-event.on" → true)
        .configure("supported.generic.event.max" → 2)
        .configure("metrics.enabled" → false)
        .build()

      val controller = application.injector.instanceOf[LiveOrchestrationController]

      val result = await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 400
    }

    "should successfully execute if the number of events to execute is less than or equal to the max event config" in {

      val request: JsValue = Json.parse(findResource(s"/resources/generic/max-event-calls-exceeded-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "SuccessMaxEvents"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Bearer 234342hh23"
      ).withJsonBody(request)

      val authConnector = mock[AuthConnector]
      val orchestrationService = mock[LiveOrchestrationService]
      when(orchestrationService.orchestrate(ArgumentMatchers.any[OrchestrationServiceRequest](), ArgumentMatchers.any[Nino](), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future(Json.obj()))

      val controller = new TestLiveOrchestrationController(authConnector, orchestrationService, 10, 3, 200, 14400, "SuccessMaxEvents")
      await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      verify(orchestrationService, times(1)).orchestrate(ArgumentMatchers.any[OrchestrationServiceRequest](), ArgumentMatchers.any[Nino](), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]())
    }

    "should successfully execute if the number of services to execute is less than or equal to the max service config" in {

      val request: JsValue = Json.parse(findResource(s"/resources/generic/max-service-calls-exceeded-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "SuccessServiceMax"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Bearer 1020202020"
      ).withJsonBody(request)

      val authConnector = mock[AuthConnector]
      val orchestrationService = mock[LiveOrchestrationService]
      when(orchestrationService.orchestrate(ArgumentMatchers.any[OrchestrationServiceRequest](), ArgumentMatchers.any[Nino](), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future(Json.obj()))

      val controller = new TestLiveOrchestrationController(authConnector, orchestrationService, 3, 10, 200, 14400, "SuccessServiceMax")

      await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      verify(orchestrationService, times(1)).orchestrate(ArgumentMatchers.any[OrchestrationServiceRequest](), ArgumentMatchers.any[Nino](), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]())
    }
  }
}