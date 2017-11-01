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

//package uk.gov.hmrc.ngc.orchestration.controllers

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{LegacyCredentials, Retrieval, ~}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.controllers.LiveOrchestrationController
import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, PreFlightCheckResponse}
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, PreFlightRequest}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class OrchestrationControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val hc = HeaderCarrier()

  val mockLiveOrchestrationService = mock[LiveOrchestrationService]
  val mockAuthConnector = mock[AuthConnector]

  val journeyId = UUID.randomUUID().toString

  def mockServicePreFlightCall(response: PreFlightCheckResponse) = {
    Mockito.when(mockLiveOrchestrationService.preFlightCheck(ArgumentMatchers.any[PreFlightRequest](), ArgumentMatchers.any[Option[String]]())(ArgumentMatchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(response))
  }

  "preFlightCheck live controller" should {

    "return the Pre-Flight Check Response successfully" in {

      val expectation = PreFlightCheckResponse(true, Accounts(Some(Nino("CS700100A")), None, false, false, journeyId, "some-cred-id", "Individual"))
      mockServicePreFlightCall(expectation)
      val versionBody = Json.parse("""{"os":"android", "version":"1.0.1"}""")
      val versionRequest = FakeRequest().withBody(versionBody).withHeaders(CONTENT_TYPE -> JSON, ACCEPT -> "application/vnd.hmrc.1.0+json")

      val controller = new LiveOrchestrationController(mockAuthConnector, mockLiveOrchestrationService, 10, 10, 200, 30000)
      val result = await(controller.preFlightCheck(Some(journeyId))(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))(Duration(10,TimeUnit.SECONDS))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.toJson(expectation)
      result.header.headers.get("Set-Cookie").get contains ("mdtpapi=")
    }
  }
}


//
//    "return empty tax-credit-summary response when retrieval of tax-summary returns non 200 response and push-registration executed successfully" in new TestGenericController {
//      override val statusCode: Option[Int] = None
//      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
//      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-summary/2017" -> false)
//      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
//      override val taxSummaryData: JsValue = TestData.taxSummaryData()
//
//      val jsonMatch = Seq(TestData.taxSummaryEmpty, TestData.taxCreditSummary, TestData.submissionStateOn, TestData.campaigns, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
//        jsonMatch, 200, """{"token":"123456"}""")(versionRequest)
//
//      pushRegistrationInvokeCount shouldBe 1
//    }
//
//    "return empty taxSummary attribute when tax-summary returns non 200 response and empty taxCreditSummary when retrieval of tax-credit-summary returns non 200 response" in new TestGenericController {
//      override val statusCode: Option[Int] = None
//      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
//      override val mapping:Map[String, Boolean] = servicesSuccessMap ++
//        Map(
//          "/income/CS700100A/tax-summary/2017" -> false,
//          "/income/CS700100A/tax-credits/tax-credits-summary" -> false
//        )
//
//      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
//      override val taxSummaryData: JsValue = TestData.taxSummaryData()
//
//      val jsonMatch = Seq(TestData.taxSummaryEmpty, TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
//        jsonMatch, 200, """{"token":"123456"}""")(versionRequest)
//
//      pushRegistrationInvokeCount shouldBe 1
//    }
//
//    "return taxCreditSummary empty JSON when the tax-credit-summary service returns a non 200 response + not invoke PushReg when payload is empty" in new TestGenericController {
//      override val statusCode : Option[Int] = None
//      override val exception :Option[Exception] = Some(new BadRequestException("controlled explosion"))
//      override val mapping :Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/tax-credits-summary" -> false)
//      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
//      override val taxSummaryData: JsValue = TestData.taxSummaryData()
//
//      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
//        jsonMatch, 200, "{}")(versionRequest)
//
//      pushRegistrationInvokeCount shouldBe 0
//    }
//
//    "return throttle status response when throttle limit has been hit" in new ThrottleLimit {
//      val result = performOrchestrate("{}", controller, controller.testSessionId, nino)
//      status(result) shouldBe 429
//      jsonBodyOf(result) shouldBe TestData.statusThrottle
//    }
//
//    "returns a success response from version-check generic service" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "GenericSuccess"
//      override val statusCode: Option[Int] = Option(200)
//      override val mapping: Map[String, Boolean] = Map("/profile/native-app/version-check" -> true)
//      override val exception: Option[Exception] = None
//      override val response: JsValue = Json.parse(findResource(s"/resources/generic/version-check.json").get)
//      val request: JsValue = Json.parse(findResource(s"/resources/generic/version-check-request.json").get)
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "returns a success response from deskpro-feedback generic service"  in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "GenericTokenSuccess"
//      override val statusCode: Option[Int] = Option(200)
//
//      override lazy val exception: Option[Exception] = None
//
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false, TestData.responseTicket)))
//
//      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-response.json").get)
//
//      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-request.json").get)
//
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//          "Accept" -> "application/vnd.hmrc.1.0+json",
//          "Authorization" -> "Some Header"
//        ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "returns a failure response from deskpro-feedback generic service" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "GenericTokenFailure"
//      override val statusCode: Option[Int] = Option(200)
//
//      override lazy val exception: Option[Exception] = None
//
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(true, TestData.responseTicket)))
//
//      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-failure-response.json").get)
//
//      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-request.json").get)
//
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//          "Accept" -> "application/vnd.hmrc.1.0+json",
//          "Authorization" -> "Some Header"
//        ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "returns multiple failure responses from deskpro-feedback generic service" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "GenericTokenMultipleFailure"
//      override val statusCode: Option[Int] = Option(200)
//
//      override lazy val exception: Option[Exception] = None
//
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(
//        Seq(GenericServiceResponse(true, TestData.responseTicket), GenericServiceResponse(true, TestData.responseTicket)))
//
//      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-multiple-failure-response.json").get)
//
//      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-multiple-request.json").get)
//
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//          "Accept" -> "application/vnd.hmrc.1.0+json",
//          "Authorization" -> "Some Header"
//        ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "returns mixture of failure and success responses from deskpro-feedback generic service" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "GenericTokenMultipleSuccessAndFailure"
//      override val statusCode: Option[Int] = Option(200)
//      override lazy val exception: Option[Exception] = None
//
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(
//        Seq(GenericServiceResponse(true, TestData.responseTicket), GenericServiceResponse(false, TestData.responseTicket)))
//      val expectedResponse = """{"OrchestrationResponse":{"response":[{"serviceName":"deskpro-feedback","failure":true},{"serviceName":"deskpro-feedback","failure":true}]},"status":{"code":"complete"}}"""
//
//      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-success-failure-response.json").get)
//
//      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-multiple-request.json").get)
//
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//          "Accept" -> "application/vnd.hmrc.1.0+json",
//          "Authorization" -> "Some Header"
//        ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "returns response of failure with a timeout flag set when a GatewayTimeoutException occurs executing the generic service" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "GenericTokenTimeoutFailure"
//      override val statusCode: Option[Int] = Option(200)
//      override lazy val exception: Option[Exception] = None
//
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(
//        Seq(GenericServiceResponse(true, TestData.responseTicket)), Some("timeout"))
//      val expectedResponse = """{"OrchestrationResponse":{"response":[{"serviceName":"deskpro-feedback","failure":true,"timeout":true}]},"status":{"code":"complete"}}"""
//
//      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-failure-timeout-response.json").get)
//
//      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-request.json").get)
//
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//
//    "returns a success response from push-notification-get-message generic service" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "push-push-notification-get-message-success"
//      override val statusCode: Option[Int] = Option(200)
//      override val mapping: Map[String, Boolean] = Map("/messages/c59e6746-9cd8-454f-a4fd-c5dc42db7d99" -> true)
//      override val exception: Option[Exception] = None
//      override lazy val response: JsValue = Json.parse(findResource(s"/resources/generic/push-notification-get-message-response.json").get)
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false,
//        (response \\ "responseData").head)))
//
//      val request: JsValue = Json.parse(findResource("/resources/generic/push-notification-get-message-request.json").get)
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "returns a success response from push-notification-respond-to-message generic service" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "push-notification-respond-to-message-success"
//      override val statusCode: Option[Int] = Some(200)
//      override val mapping: Map[String, Boolean] = Map("/messages/c59e6746-9cd8-454f-a4fd-c5dc42db7d99/response" -> true)
//      override val exception: Option[Exception] = None
//      override lazy val response: JsValue = Json.parse(findResource(s"/resources/generic/push-notification-respond-to-message-response.json").get)
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false,
//        (response \\ "responseData").head)))
//
//      val request: JsValue = Json.parse(findResource("/resources/generic/push-notification-respond-to-message-request.json").get)
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response, 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "return success response from claimant-details service" in new TestGenericOrchestrationController with FileResource {
//      lazy val claimantDetails: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details.json").get)
//
//      override lazy val test_id: String = "claimant-details-success"
//      override val exception: Option[Exception] = None
//      override val statusCode: Option[Int] = Option(200)
//      override lazy val response: JsValue = Json.parse(findResource(s"/resources/generic/tax-credit-claimant-details-response.json").get)
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(
//        GenericServiceResponse(failure = false, (claimantDetails \\ "firstCall").head),
//        GenericServiceResponse(failure = false, Json.parse("""{"tcrAuthToken": "Basic Q1M3MDAxMDBBOjIwMDAwMDAwMDAwMDAxMw=="}""")),
//        GenericServiceResponse(failure = false, (claimantDetails \\ "secondCall").head)
//      ))
//
//      val request: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details-request.json").get)
//      val fakeRequest: FakeRequest[AnyContentAsJson] = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "return success response from claimant-details service given a problem with the tcrAuthToken" in new TestGenericOrchestrationController with FileResource {
//      lazy val claimantDetails: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details.json").get)
//      lazy val fullResponse: String = findResource(s"/resources/generic/tax-credit-claimant-details-response.json").get
//      lazy val trim: UnanchoredRegex = """(?s)FirstSpecifiedDate[^,]+,.*?"renewalForm[^}]+""".r.unanchored
//
//      override lazy val test_id: String = "claimant-details-no-auth"
//      override val exception: Option[Exception] = None
//      override val statusCode: Option[Int] = Option(200)
//      override lazy val response: JsValue = Json.parse(trim.replaceAllIn(fullResponse, """FirstSpecifiedDate": "12/10/2010""""))
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(
//        GenericServiceResponse(failure = false, (claimantDetails \\ "firstCall").head),
//        GenericServiceResponse(failure = true, Json.parse("""{"ERROR" : "Broken token"}"""))
//      ))
//
//      val request: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details-request.json").get)
//      val fakeRequest: FakeRequest[AnyContentAsJson] = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "return success response from claimant-details service given a problem with the claimant-details" in new TestGenericOrchestrationController with FileResource {
//      lazy val claimantDetails: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details.json").get)
//      lazy val fullResponse: String = findResource(s"/resources/generic/tax-credit-claimant-details-response.json").get
//      lazy val trim: UnanchoredRegex = """(?s)FirstSpecifiedDate[^,]+,.*?"renewalForm[^}]+""".r.unanchored
//
//      override lazy val test_id: String = "claimant-details-no-details"
//      override val exception: Option[Exception] = None
//      override val statusCode: Option[Int] = Option(200)
//      override lazy val response: JsValue = Json.parse(trim.replaceAllIn(fullResponse, """FirstSpecifiedDate": "12/10/2010""""))
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(
//        GenericServiceResponse(failure = false, (claimantDetails \\ "firstCall").head),
//        GenericServiceResponse(failure = false, Json.parse("""{"tcrAuthToken": "Basic Q1M3MDAxMDBBOjIwMDAwMDAwMDAwMDAxMw=="}""")),
//        GenericServiceResponse(failure = true, Json.parse("""{"ERROR" : "Broken details"}"""))
//      ))
//
//      val request: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details-request.json").get)
//      val fakeRequest: FakeRequest[AnyContentAsJson] = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "returns a success response from audit event request generic executor" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "audit-event-request-success"
//      override val statusCode: Option[Int] = Option(200)
//      override val mapping: Map[String, Boolean] = Map()
//      override val exception: Option[Exception] = None
//      override lazy val response: JsValue = Json.parse(findResource(s"/resources/generic/audit-event-executor-response.json").get)
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false,response)))
//
//      val request: JsValue = Json.parse(findResource("/resources/generic/audit-event-executor-request.json").get)
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//    "returns mixture of service and event responses from generic orchestration" in new TestGenericOrchestrationController with FileResource {
//      override lazy val test_id: String = "generic-service-and-event-execution-response-success"
//      override val statusCode: Option[Int] = Option(200)
//      override lazy val exception: Option[Exception] = None
//
//      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false, TestData.responseTicket)))
//
//      override val response: JsValue = Json.parse(findResource(s"/resources/generic/generic-service-and-event-executor-response.json").get)
//
//      val request: JsValue = Json.parse(findResource(s"/resources/generic/generic-service-and-event-executor-request.json").get)
//
//      val fakeRequest = FakeRequest().withSession(
//        "AuthToken" -> "Some Header"
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        "Authorization" -> "Some Header"
//      ).withJsonBody(request)
//
//      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
//    }
//
//
//    "Simulating concurrent http requests through the async framework " should {
//
//      "successfully process all concurrent requests and once all tasks are complete, verify the throttle value is 0" in {
//
//        def createController(counter: Int) = {
//          new TestGenericController {
//            override val time = counter.toLong
//            override lazy val test_id = s"test_id_concurrent_$counter"
//            override val exception: Option[Exception] = None
//            override val statusCode: Option[Int] = None
//            override val mapping: Map[String, Boolean] = servicesSuccessMap
//            override val taxSummaryData: JsValue = TestData.taxSummaryData(Some(test_id))
//          }
//        }
//
//        executeParallelAsyncTasks(createController, "async_native-apps-api-id-test_id_concurrent")
//      }
//
//      def executeParallelAsyncTasks(generateController: => Int => TestGenericController, asyncTaskId:String) = {
//        val timeStart = System.currentTimeMillis()
//
//        val concurrentRequests = (0 until 20).foldLeft(Seq.empty[TestGenericController]) {
//          (list, counter) => {
//            list ++ Seq(generateController(counter))
//          }
//        }
//
//        val result = concurrentRequests.map { asyncTestRequest =>
//          val delay = scala.util.Random.nextInt(50)
//          TimedEvent.delayedSuccess(delay, 0).map(a => {
//            implicit val reqImpl = asyncTestRequest.requestWithAuthSession
//
//            // Build the expected response.
//            // Note: The specific async response is validated against the JSON generated server side contains the task Id.
//            val jsonMatch = Seq(TestData.taxSummary(Some(asyncTestRequest.controller.testSessionId)), TestData.taxCreditSummary, TestData.submissionStateOn, TestData.campaigns, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)
//
//            // Execute the controller async request and poll for response.
//            val task_id = s"${asyncTaskId}_${asyncTestRequest.time}"
//            invokeOrchestrateAndPollForResult(asyncTestRequest.controller, task_id, Nino("CS700100A"),
//              jsonMatch, 200, "{}")(asyncTestRequest.versionRequest)
//          })
//        }
//
//        eventually(Timeout(Span(95000, Milliseconds)), Interval(Span(2, Seconds))) {
//          await(Future.sequence(result))
//        }
//
//        uk.gov.hmrc.play.asyncmvc.async.Throttle.current shouldBe 0
//        println("Time spent processing... " + (System.currentTimeMillis() - timeStart))
//      }
//    }
//  }
//
//  "orchestrate live controller authentication " should {
//
//    "return unauthorized when authority record does not contain a NINO" in new AuthWithoutNino {
//      testNoNINO(await(controller.orchestrate(nino)(emptyRequestWithHeader)))
//    }
//
//    "return 401 result with json status detailing low CL on authority" in new AuthWithLowCL {
//      testLowCL(await(controller.orchestrate(nino)(emptyRequestWithHeader)))
//    }
//
//    "return status code 406 when the headers are invalid" in new Success {
//      val result = await(controller.orchestrate(nino)(emptyRequest))
//      status(result) shouldBe 406
//    }
//  }
//
//  "sandbox controller " should {
//
//    "return the PreFlightCheckResponse response from a static resource" in new SandboxSuccess {
//      val result = await(controller.preFlightCheck(Some(journeyId))(requestWithAuthSession.withBody(versionBody)))
//      status(result) shouldBe 200
//      val journeyIdRetrieve: String = (contentAsJson(result) \ "accounts" \ "journeyId").as[String]
//      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(nino), None, routeToIV = false, routeToTwoFactor = false, journeyIdRetrieve, "someCredId", "Individual")))
//    }
//
//    "return startup response from a static resource" in new SandboxSuccess {
//      val result = await(controller.orchestrate(nino)(requestWithAuthSession.withJsonBody(Json.parse("""{}"""))))
//      status(result) shouldBe 200
//      contentAsJson(result) shouldBe TestData.sandboxStartupResponse
//    }
//
//    "return poll response from a static resource" in new SandboxSuccess {
//      val currentTime = (new LocalDate()).toDateTimeAtStartOfDay
//      val result = await(controller.poll(nino)(requestWithAuthSession))
//      status(result) shouldBe 200
//      contentAsJson(result) shouldBe Json.parse(TestData.sandboxPollResponse
//        .replaceAll("previousDate1", currentTime.minusWeeks(2).getMillis.toString)
//        .replaceAll("previousDate2", currentTime.minusWeeks(1).getMillis.toString)
//        .replaceAll("previousDate3", currentTime.getMillis.toString)
//        .replaceAll("date1", currentTime.plusWeeks(1).getMillis.toString)
//        .replaceAll("date2", currentTime.plusWeeks(2).getMillis.toString)
//        .replaceAll("date3", currentTime.plusWeeks(3).getMillis.toString)
//        .replaceAll("date4", currentTime.plusWeeks(4).getMillis.toString)
//        .replaceAll("date5", currentTime.plusWeeks(5).getMillis.toString)
//        .replaceAll("date6", currentTime.plusWeeks(6).getMillis.toString)
//        .replaceAll("date7", currentTime.plusWeeks(7).getMillis.toString)
//        .replaceAll("date8", currentTime.plusWeeks(8).getMillis.toString))
//
//      result.header.headers.get("Cache-Control") shouldBe Some("max-age=14400")
//    }
//  }
//
//  val token = "Bearer 123456789"
//  def performOrchestrate(inputBody: String, controller: NativeAppsOrchestrationController, testSessionId: String, nino: Nino,
//                         journeyId: Option[String] = None) = {
//    val authToken = "AuthToken" -> token
//    val authHeader = "Authorization" -> token
//    val body: JsValue = Json.parse(inputBody)
//
//    val requestWithSessionKeyAndId = FakeRequest()
//      .withSession(
//        authToken
//      ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        authHeader
//      ).withJsonBody(body)
//
//    await(controller.orchestrate(nino, journeyId).apply(requestWithSessionKeyAndId))
//  }
//
//  def invokeOrchestrateAndPollForResult(controller: NativeAppsOrchestrationController, testSessionId: String, nino: Nino, response: JsValue, resultCode: Int = 200,
//                                        inputBody: String = """{"token":"123456"}""", cacheHeader : Option[String] = Some("max-age=14400"),
//                                         overrideNino:Option[Nino]=None)(implicit request: Request[_]) = {
//    val authToken = "AuthToken" -> token
//    val authHeader = "Authorization" -> token
//
//    val requestWithSessionKeyAndIdNoBody = FakeRequest().withSession(
//      controller.AsyncMVCSessionId -> controller.buildSession(controller.id, testSessionId),
//      authToken
//    ).withHeaders(
//        "Accept" -> "application/vnd.hmrc.1.0+json",
//        authHeader
//      )
//
//    // Perform startup request.
//    val startupResponse = performOrchestrate(inputBody, controller, testSessionId, nino)
//    status(startupResponse) shouldBe 200
//
//    // Verify the Id within the session matches the expected test Id.
//    val session = startupResponse.session.get(controller.AsyncMVCSessionId)
//
//    val jsonSession = Json.parse(session.get).as[AsyncMvcSession]
//    jsonSession.id shouldBe testSessionId
//
//    // Poll for the result.
//    eventually(Timeout(Span(10, Seconds))) {
//      val pollResponse: Result = await(controller.poll(overrideNino.getOrElse(nino))(requestWithSessionKeyAndIdNoBody))
//
//      status(pollResponse) shouldBe resultCode
//      if (resultCode!=401) {
//        jsonBodyOf(pollResponse) shouldBe response
//
//        pollResponse.header.headers.get("Cache-Control") shouldBe cacheHeader
//      }
//    }
//  }
//
//}
