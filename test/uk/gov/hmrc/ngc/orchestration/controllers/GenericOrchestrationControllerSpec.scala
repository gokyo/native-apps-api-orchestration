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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.ngc.orchestration.controllers.LiveOrchestrationController
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class GenericOrchestrationControllerSpec extends UnitSpec with WithFakeApplication with FileResource {

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

    "execute service version-check posting the post request data" in {

      val request: JsValue = Json.parse(findResource(s"/resources/generic/version-check-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val application = new GuiceApplicationBuilder()
        .configure("supported.generic.service.version-check.on" → true)
        .configure("metrics.enabled" → false)
        .build()

      val controller = application.injector.instanceOf[LiveOrchestrationController]

      val result = await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe pollResponse
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

    "should successfully execute if the number of services to execute is less than or equal to the max service config" in {

      val request: JsValue = Json.parse(findResource(s"/resources/generic/max-service-calls-exceeded-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val application = new GuiceApplicationBuilder()
        .configure("supported.generic.service.version-check.on" → true)
        .configure("supported.generic.service.max" → 3)
        .configure("metrics.enabled" → false)
        .build()

      val controller = application.injector.instanceOf[LiveOrchestrationController]

      val result = await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe pollResponse
    }

    "should successfully execute if the number of events to execute is less than or equal to the max event config" in {

      val request: JsValue = Json.parse(findResource(s"/resources/generic/max-event-calls-exceeded-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val application = new GuiceApplicationBuilder()
        .configure("supported.generic.event.max" → 3)
        .configure("supported.generic.event.ngc-audit-event.on" → true)
        .configure("metrics.enabled" → false)
        .build()

      val controller = application.injector.instanceOf[LiveOrchestrationController]

      val result = await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe pollResponse
    }
  }

  //TODO Fix this
//  "Sandbox orchestration controller" should {
//
//    "for tax credit claimant details do return sandbox data" in new SandboxSuccess {
//      val statusCode = 200
//      val response = TestData.pollResponse
//
//      val request = Json.parse(findResource("/resources/generic/tax-credit-claimant-details-request.json").get)
//
//      val fakeRequest = FakeRequest().withHeaders(
//        "Accept" → "application/vnd.hmrc.1.0+json",
//        "Authorization" → "Some Header",
//        "X-MOBILE-USER-ID" → "404893573708"
//      ).withJsonBody(request)
//
//      val result = await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
//      status(result) shouldBe statusCode
//      contentAsJson(result) shouldBe response
//    }
//  }
}
