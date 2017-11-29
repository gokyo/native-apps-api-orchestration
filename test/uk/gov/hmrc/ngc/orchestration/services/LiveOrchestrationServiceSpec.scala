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

package uk.gov.hmrc.ngc.orchestration.services

import java.util.UUID

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.{Answer, OngoingStubbing}
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{GGCredId, LegacyCredentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, ConfidenceLevel}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.controllers.TestData
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class LiveOrchestrationServiceSpec extends UnitSpec with WithFakeApplication with MockitoSugar with FileResource {


  trait mocks {
    implicit val mockMFAIntegration: MFAIntegration = mock[MFAIntegration]
    implicit val mockGenericConnector: GenericConnector = mock[GenericConnector]
    implicit val mockAuditConnector: AuditConnector = mock[AuditConnector]
    implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  }
  
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val nino = "CS700100A"
  val host = "localhost"
  val port = 33333
  val randomUUID: String = UUID.randomUUID().toString
  val journeyId = s"?journeyId=$randomUUID"
  val pushNotificationMessageId = "c59e6746-9cd8-454f-a4fd-c5dc42db7d99"
  val validBarCode = "200000000000013"
  val legacyRequest: JsValue = Json.obj("device" → Json.parse("""{"osVersion":"10.3.3","os":"ios","appVersion":"4.9.0","model":"iPhone8,2"}"""),
                                "token"  → JsString("cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"))

  val taxSummarySubmissionState: String = s"/income/tax-credits/submission/state/enabled$journeyId"
  val pushRegistration: String = s"/push/registration$journeyId"
  val pushNotificationGetMessage: String = s"/messages/$pushNotificationMessageId$journeyId"
  val pushNotificationResponseToMessage: String = s"/messages/$pushNotificationMessageId/response$journeyId"
  val taxCreditsBarCode = s"/income/$nino/tax-credits/$validBarCode/auth$journeyId"
  val versionCheck: String = "/profile/native-app/version-check"
  val deskproFeedback: String = "/deskpro/feedback"

  def taxSummary(nino: String, year: Int): String = s"/income/$nino/tax-summary/$year$journeyId"
  def taxCreditDecision(nino: String): String = s"/income/$nino/tax-credits/tax-credits-decision$journeyId"
  def taxCreditSummary(nino: String): String = s"/income/$nino/tax-credits/tax-credits-summary$journeyId"
  def claimantDetailsClaims(nino: String): String = s"/income/$nino/tax-credits/claimant-details?claims=true&journeyId=$randomUUID"
  def claimantDetails(nino: String): String = s"/income/$nino/tax-credits/claimant-details$journeyId"

  def stubHostAndPortGenericConnector()(implicit genericConnector: GenericConnector): OngoingStubbing[Int] = {
    when(genericConnector.host(ArgumentMatchers.anyString())).thenReturn(host)
    when(genericConnector.port(ArgumentMatchers.anyString())).thenReturn(port)
  }
  def stubGETGenericConnectorResponse(path: String, response: JsValue)(implicit genericConnector: GenericConnector): OngoingStubbing[Future[JsValue]] = {
    when(genericConnector.doGet(ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future(response))
  }

  def stubGETGenericConnectorFailure(path: String, status: Int)(implicit genericConnector: GenericConnector): Any = {
    if(status >= 400 && status < 500) {
      when(genericConnector.doGet(ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(Upstream4xxResponse("", status, status)))
    }
    if(status == 500){
      when(genericConnector.doGet(ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(Upstream5xxResponse("", status, status)))
    }
  }

  def stubPOSTGenericConnectorResponse(path: String, response: JsValue)(implicit genericConnector: GenericConnector): OngoingStubbing[Future[JsValue]] = {
    when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(response))
  }

  def stubPOSTGenericConnectorSuccessAndFailureResponse(path: String, failAfterTimes: Int, status: Int, responses: JsValue*)(implicit genericConnector: GenericConnector): OngoingStubbing[Future[JsValue]] = {
    val answer = new Answer[Future[Any]] {
      var counter: Int = 0
      override def answer(invocation: InvocationOnMock): Future[Any] = {
        if (counter < failAfterTimes){
          counter += 1
          Future.successful(responses(counter - 1))
        }
        else {
          counter += 1
          if(status >= 400 && status < 500) Future.failed(Upstream4xxResponse("", status, status))
          if(status == 500) Future.failed(Upstream5xxResponse("", status, status))
        }
      }
    }
    when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
      .thenAnswer(answer)
  }

  def stubPOSTGenericConnectorFailure(path: String, status: Int)(implicit genericConnector: GenericConnector): Any = {
    if(status >= 400 && status < 500) {
      when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(Upstream4xxResponse("", status, status)))
    }
    if(status == 500){
      when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(Upstream5xxResponse("", status, status)))
    }
    if(status == 504){
      when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new GatewayTimeoutException("")))
    }
  }

  type GrantAccess = Option[String] ~ ConfidenceLevel
  def stubAuthorisationGrantAccess(response: GrantAccess)(implicit authConnector: AuthConnector): OngoingStubbing[Future[GrantAccess]] = {
    when(authConnector.authorise(ArgumentMatchers.any[Predicate](), ArgumentMatchers.any[Retrieval[GrantAccess]]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(response))
  }

  type GetAccounts = Option[String] ~ Option[String] ~ Option[AffinityGroup] ~ LegacyCredentials ~ Option[String] ~ ConfidenceLevel
  def stubAuthorisationGetAccounts(response: GetAccounts)(implicit authConnector: AuthConnector) = {
    when(authConnector.authorise(ArgumentMatchers.any[Predicate](), ArgumentMatchers.any[Retrieval[GetAccounts]]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(response))
  }

  def stubAuthorisationGetAccountsFailure(status: Int)(implicit authConnector: AuthConnector) = {
    when(authConnector.authorise(ArgumentMatchers.any[Predicate](), ArgumentMatchers.any[Retrieval[GetAccounts]]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future.failed(Upstream4xxResponse("", 400, 400)))
  }


  "LiveOrchestrationService.preFlight" should {

    "return the response with default value when version-check fails" in new mocks {

      val getAccountsResponse: GetAccounts = Some(nino) and None and Some(Individual) and GGCredId("some-cred-id") and Some("strong") and ConfidenceLevel.L200
      stubAuthorisationGetAccounts(getAccountsResponse)
      stubPOSTGenericConnectorFailure(s"$versionCheck$journeyId", 500)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response = await(liveOrchestrationService.preFlightCheck(PreFlightRequest(os = "ios or android", version = "n.n.n", None ), Some(randomUUID)))
      response.upgradeRequired shouldBe false
    }
  }

  "LiveOrchestrationService.startup" should {
    "return no taxCreditSummary or related Campaigns attribute when service call to " +
      "'/income/:nino/tax-credits/tax-credits-decision' endpoint throws 400 exception" in new mocks {
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorFailure(taxCreditDecision(nino), 400)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val json: String = """{
                          |  "device": {
                          |    "osVersion": "10.3.3",
                          |    "os": "ios",
                          |    "appVersion": "4.9.0",
                          |    "model": "iPhone8,2"
                          |  },
                          |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                          |}""".stripMargin
      val request = OrchestrationServiceRequest(requestLegacy = Some(legacyRequest), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 0
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 0
    }

    "return no taxCreditSummary or related Campaigns attribute when service call to " +
      "'/income/:nino/tax-credits/tax-credits-decision' endpoint throws 404 exception" in new mocks {
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorFailure(taxCreditDecision(nino), 404)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val json: String = """{
                   |  "device": {
                   |    "osVersion": "10.3.3",
                   |    "os": "ios",
                   |    "appVersion": "4.9.0",
                   |    "model": "iPhone8,2"
                   |  },
                   |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                   |}""".stripMargin
      val request = OrchestrationServiceRequest(requestLegacy = Some(legacyRequest), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 0
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 0
    }

    "return no taxCreditSummary or related Campaigns attribute when service call to " +
      "'/income/:nino/tax-credits/tax-credits-decision' endpoint throws 401 exception" in new mocks {
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorFailure(taxCreditDecision(nino), 404)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val json: String = """{
                   |  "device": {
                   |    "osVersion": "10.3.3",
                   |    "os": "ios",
                   |    "appVersion": "4.9.0",
                   |    "model": "iPhone8,2"
                   |  },
                   |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                   |}""".stripMargin
      val request = OrchestrationServiceRequest(requestLegacy = Some(legacyRequest), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 0
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 0
    }

    "return taxCreditSummary attribute when service call to " +
      "'/income/tax-credits/submission/state/enabled' endpoint returns a non 200 response" in new mocks {
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorResponse(taxCreditDecision(nino), TestData.testTaxCreditDecision)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorFailure(taxSummarySubmissionState, 404)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val request = OrchestrationServiceRequest(requestLegacy = Some(legacyRequest), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 1
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 1
    }

    "response data is not effected by a failure to execute push-registration service" in new mocks {
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorResponse(taxCreditDecision(nino), TestData.testTaxCreditDecision)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorFailure(pushRegistration, 400)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val request = OrchestrationServiceRequest(requestLegacy = Some(legacyRequest), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 1
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 1
    }

    "return empty taxSummary attribute when '/income/:nino/tax-summary/$year' service endpoint returns non 200 response " +
      "and an empty taxCreditSummary and no related Campaigns when '/income/:nino/tax-credits/tax-credits-summary' " +
      "service endpoint returns non 200 response" in new mocks {
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorFailure(taxSummary(nino, 2017), 400)
      stubGETGenericConnectorResponse(taxCreditDecision(nino), TestData.testTaxCreditDecision)
      stubGETGenericConnectorFailure(taxCreditSummary(nino), 500)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val request = OrchestrationServiceRequest(requestLegacy = Some(legacyRequest), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").head  shouldBe Json.obj()
      (response \\ "taxCreditSummary").head shouldBe Json.obj()
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 0
    }

    "return taxCreditSummary empty JSON when the tax-credit-summary service returns a non 200 response + not invoke PushReg when payload is empty" in new mocks {
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorFailure(taxSummary(nino, 2017), 400)
      stubGETGenericConnectorResponse(taxCreditDecision(nino), TestData.testTaxCreditDecision)
      stubGETGenericConnectorFailure(taxCreditSummary(nino), 500)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val request = OrchestrationServiceRequest(requestLegacy = Some(Json.obj()), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").head  shouldBe Json.obj()
      (response \\ "taxCreditSummary").head shouldBe Json.obj()
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 0
      verify(mockGenericConnector, times(0))
        .doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq(pushRegistration), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]())
    }
  }

  "LiveOrchestrationService.orchestrate" should {
    "returns a success response from 'deskpro-feedback' generic service" in new mocks {
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse("/deskpro/feedback", Json.obj("ticket_id" → 1980683879))
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val feedbackRequest = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id" → "Some feedback data")))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(feedbackRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.stringify(Json.toJson(response)) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"deskpro-feedback","responseData":{"ticket_id":1980683879},"failure":false}]}}"""
    }

    "returns a failure response from deskpro-feedback generic service" in new mocks {
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorFailure("/deskpro/feedback", 500)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val feedbackRequest = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id" → "Some feedback data")))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(feedbackRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.stringify(Json.toJson(response)) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"deskpro-feedback","failure":true,"timeout":false}]}}"""
    }

    "returns multiple failure responses from deskpro-feedback generic service" in new mocks {
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorFailure("/deskpro/feedback", 500)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val feedbackRequest_1 = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id_1" → "Some feedback data A")))
      val feedbackRequest_2 = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id_2" → "Some feedback data B")))
      val feedbackRequest_3 = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id_3" → "Some feedback data C")))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(feedbackRequest_1, feedbackRequest_2, feedbackRequest_3)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.stringify(Json.toJson(response)) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"deskpro-feedback","failure":true,"timeout":false},{"name":"deskpro-feedback","failure":true,"timeout":false},{"name":"deskpro-feedback","failure":true,"timeout":false}]}}"""
    }

    "returns mixture of failure and success responses from deskpro-feedback generic service" in new mocks {
      val response1: JsObject = Json.obj("ticket_id" → 111111111)
      val response2: JsObject = Json.obj("ticket_id" → 222222222)
      val response3: JsObject = Json.obj("ticket_id" → 333333333)
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorSuccessAndFailureResponse("/deskpro/feedback", 3, 500, response1, response2, response3)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val feedbackRequest_1 = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id_1" → "Some feedback data A")))
      val feedbackRequest_2 = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id_2" → "Some feedback data B")))
      val feedbackRequest_3 = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id_3" → "Some feedback data C")))
      val feedbackRequest_4 = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id_4" → "Some feedback data D")))
      val feedbackRequest_5 = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id_5" → "Some feedback data E")))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(feedbackRequest_1, feedbackRequest_2, feedbackRequest_3, feedbackRequest_4, feedbackRequest_5)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.stringify(Json.toJson(response)) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"deskpro-feedback","responseData":{"ticket_id":111111111},"failure":false},{"name":"deskpro-feedback","responseData":{"ticket_id":222222222},"failure":false},{"name":"deskpro-feedback","responseData":{"ticket_id":333333333},"failure":false},{"name":"deskpro-feedback","failure":true,"timeout":false},{"name":"deskpro-feedback","failure":true,"timeout":false}]}}"""
    }

    "returns response of failure with a timeout flag set when a GatewayTimeoutException occurs executing the generic service" in new mocks {
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorFailure("/deskpro/feedback", 504)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val feedbackRequest = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id" → "Some feedback data")))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(feedbackRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.stringify(Json.toJson(response)) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"deskpro-feedback","failure":true,"timeout":true}]}}"""
    }

    "returns a success response from push-notification-get-message generic service" in new mocks {
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(pushNotificationGetMessage, Json.parse("""{"id":"msg-some-id","subject":"Weather","body":"Is it raining?","responses":{"yes":"Yes","no":"No"}}"""))
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val pushNotificationGetMessageRequest = ExecutorRequest(name = "push-notification-get-message", data = Some(Json.obj("messageId" → pushNotificationMessageId)))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(pushNotificationGetMessageRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.stringify(Json.toJson(response)) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"push-notification-get-message","responseData":{"id":"msg-some-id","subject":"Weather","body":"Is it raining?","responses":{"yes":"Yes","no":"No"}},"failure":false}]}}"""
    }

    "returns a success response from push-notification-respond-to-message generic service" in new mocks {
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(pushNotificationResponseToMessage, Json.obj())
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val pushNotificationGetMessageRequest = ExecutorRequest(name = "push-notification-respond-to-message", data = Some(Json.obj("messageId" → pushNotificationMessageId)))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(pushNotificationGetMessageRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.stringify(Json.toJson(response)) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"push-notification-respond-to-message","responseData":{},"failure":false}]}}"""
    }

    "return success response from claimant-details service" in new mocks {
      val responseJson: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details.json").get)
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(claimantDetailsClaims(nino), (responseJson \\ "firstCall").head)
      stubGETGenericConnectorResponse(taxCreditsBarCode, Json.parse("""{"tcrAuthToken": "Basic Q1M3MDAxMDBBOjIwMDAwMDAwMDAwMDAxMw=="}"""))
      stubGETGenericConnectorResponse(claimantDetails(nino), (responseJson \\ "secondCall").head)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val claimantDetailsRequest = new ExecutorRequest(name = "claimant-details", None)
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(claimantDetailsRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.toJson(response) shouldBe Json.parse(findResource("/resources/generic/tax-credit-claimant-details-response.json").get)
    }

    "return success response from claimant-details service showing the data from the first call " +
      "given a problem with the tcrAuthToken and the second call could not be made" in new mocks {
      val responseJson: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details.json").get)
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(claimantDetailsClaims(nino), (responseJson \\ "firstCall").head)
      stubGETGenericConnectorFailure(taxCreditsBarCode, 500)
      stubGETGenericConnectorResponse(claimantDetails(nino), (responseJson \\ "secondCall").head)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val claimantDetailsRequest = new ExecutorRequest(name = "claimant-details", None)
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(claimantDetailsRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val expectedResponse = Json.obj("OrchestrationResponse" → OrchestrationResponse(serviceResponse = Some(Seq(ExecutorResponse("claimant-details", Some((responseJson \\ "firstCall").head), failure = Some(false))))))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.toJson(response) shouldBe Json.toJson(expectedResponse)
    }

    "return success response from claimant-details service showing the data from the first call " +
      "given a problem with the second call to claimant-details" in new mocks {
      val responseJson: JsValue = Json.parse(findResource("/resources/generic/tax-credit-claimant-details.json").get)
      stubHostAndPortGenericConnector()
      stubGETGenericConnectorResponse(claimantDetailsClaims(nino), (responseJson \\ "firstCall").head)
      stubGETGenericConnectorResponse(taxCreditsBarCode, Json.parse("""{"tcrAuthToken": "Basic Q1M3MDAxMDBBOjIwMDAwMDAwMDAwMDAxMw=="}"""))
      stubGETGenericConnectorFailure(claimantDetails(nino), 500)
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val claimantDetailsRequest = new ExecutorRequest(name = "claimant-details", None)
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(claimantDetailsRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val expectedResponse = Json.obj("OrchestrationResponse" → OrchestrationResponse(serviceResponse = Some(Seq(ExecutorResponse("claimant-details", Some((responseJson \\ "firstCall").head), failure = Some(false))))))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.toJson(response) shouldBe Json.toJson(expectedResponse)
    }

    "returns a success response from version-check generic service" in new mocks {
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(versionCheck, Json.obj("upgrade" → false))
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val versionRequest = ExecutorRequest(name = "version-check", data = Some(Json.obj("os" → "ios or android", "version" → "n.n.n")))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(versionRequest)), None)
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      Json.stringify(response) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"version-check","responseData":{"upgrade":false},"failure":false}]}}"""
    }

    "returns a success response from audit event request generic executor" in new mocks {
      stubHostAndPortGenericConnector()
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val auditEventRequest = ExecutorRequest(name = "ngc-audit-event",
        data = Some(Json.obj("nino" → nino,
        "generatedAt" → "2017-09-12T08:35:49.340Z",
        "detail" → Json.obj("ctcFrequency" → "WEEKLY", "wtcFrequency" → "NONE"),
        "auditType" → "TCSPayments")))
      val orchestrationRequest = new OrchestrationRequest(None, eventRequest = Some(Seq(auditEventRequest)))
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      verify(mockAuditConnector, times(1)).sendEvent(ArgumentMatchers.any[DataEvent]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]())
      Json.stringify(response) shouldBe """{"OrchestrationResponse":{"eventResponse":[{"name":"ngc-audit-event","failure":false}]}}"""
    }

    "successfully execute a mixture of service and event requests" in new mocks {
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(deskproFeedback, Json.obj("ticket_id" → 1980683879))
      stubAuthorisationGrantAccess(Some(nino) and ConfidenceLevel.L200)
      val auditEventRequest = ExecutorRequest(name = "ngc-audit-event",
        data = Some(Json.obj("nino" → nino,
          "generatedAt" → "2017-09-12T08:35:49.340Z",
          "detail" → Json.obj("ctcFrequency" → "WEEKLY", "wtcFrequency" → "NONE"),
          "auditType" → "TCSPayments")))
      val feedbackRequest = ExecutorRequest(name = "deskpro-feedback", data = Some(Json.obj("some-id" → "Some feedback data")))
      val orchestrationRequest = new OrchestrationRequest(serviceRequest = Some(Seq(feedbackRequest)), eventRequest = Some(Seq(auditEventRequest)))
      val request = OrchestrationServiceRequest(None, request = Some(orchestrationRequest))
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response: JsObject = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      verify(mockAuditConnector, times(1)).sendEvent(ArgumentMatchers.any[DataEvent]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]())
      Json.stringify(response) shouldBe """{"OrchestrationResponse":{"serviceResponse":[{"name":"deskpro-feedback","responseData":{"ticket_id":1980683879},"failure":false}],"eventResponse":[{"name":"ngc-audit-event","failure":false}]}}"""
    }
  }
}
