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
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthConnector, ConfidenceLevel}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.controllers.TestData
import uk.gov.hmrc.ngc.orchestration.services.live.MFAIntegration
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class LiveOrchestrationServiceSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  val mockMFAIntegration = mock[MFAIntegration]
  val mockGenericConnector = mock[GenericConnector]
  val mockAuditConnector = mock[AuditConnector]
  val mockAuthConnector = mock[AuthConnector]
  implicit val hc = HeaderCarrier()
  val host = "localhost"
  val port = 33333
  val randomUUID: String = UUID.randomUUID().toString
  val journeyId = s"?journeyId=$randomUUID"
  def taxSummary(nino: String, year: Int): String = s"/income/$nino/tax-summary/$year$journeyId"
  def taxCreditDecision(nino: String): String = s"/income/$nino/tax-credits/tax-credits-decision$journeyId"
  def taxCreditSummary(nino: String): String = s"/income/$nino/tax-credits/tax-credits-summary$journeyId"
  def taxSummarySubmissionState: String = s"/income/tax-credits/submission/state/enabled$journeyId"
  def pushRegistration: String = s"/push/registration$journeyId"

  def stubHostAndPortGenericConnector = {
    when(mockGenericConnector.host(ArgumentMatchers.anyString())).thenReturn(host)
    when(mockGenericConnector.port(ArgumentMatchers.anyString())).thenReturn(port)
  }
  def stubGETGenericConnectorResponse(path: String, response: JsValue) = {
    when(mockGenericConnector.doGet(ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future(response))
  }

  def stubGETGenericConnectorFailure(path: String, status: Int) = {
    if(status >= 400 && status < 500) {
      when(mockGenericConnector.doGet(ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new Upstream4xxResponse("", status, status)))
    }
  }

  def stubPOSTGenericConnectorResponse(path: String, response: JsValue) = {
    when(mockGenericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(response))
  }

  def stubPOSTGenericConnectorFailure(path: String, status: Int) = {
    if(status >= 400 && status < 500) {
      when(mockGenericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new Upstream4xxResponse("", status, status)))
    }
  }

  type GrantAccess = ~[~[Option[String], ConfidenceLevel], Option[String]]
  def stubAuthorisationGrantAccess(response: GrantAccess) = {
    when(mockAuthConnector.authorise(ArgumentMatchers.any[Predicate](), ArgumentMatchers.any[Retrieval[GrantAccess]]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(response))
  }


  "LiveOrchestrationService.preFlight" should {
    "test something" in {

//      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, "localhost", Integer.randomInt)

    }
  }

  "LiveOrchestrationService.startup" should {
    "return no taxCreditSummary attribute when service call to " +
      "'/income/$nino/tax-credits/tax-credits-decision' endpoint throws 400 exception" in {
      val nino = "CS700100A"
      stubHostAndPortGenericConnector
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorFailure(taxCreditDecision(nino), 400)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(new ~(new ~(Some(nino), ConfidenceLevel.L200), Some("creds")))
      val json = """{
                          |  "device": {
                          |    "osVersion": "10.3.3",
                          |    "os": "ios",
                          |    "appVersion": "4.9.0",
                          |    "model": "iPhone8,2"
                          |  },
                          |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                          |}""".stripMargin
      val request = OrchestrationServiceRequest(requestLegacy = Some(Json.toJson(json)), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 0
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 1
    }

    "return no taxCreditSummary attribute when service call to " +
      "'/income/$nino/tax-credits/tax-credits-decision' endpoint throws 404 exception" in {
      val nino = "CS700100A"
      stubHostAndPortGenericConnector
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorFailure(taxCreditDecision(nino), 404)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(new ~(new ~(Some(nino), ConfidenceLevel.L200), Some("creds")))
      val json = """{
                   |  "device": {
                   |    "osVersion": "10.3.3",
                   |    "os": "ios",
                   |    "appVersion": "4.9.0",
                   |    "model": "iPhone8,2"
                   |  },
                   |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                   |}""".stripMargin
      val request = OrchestrationServiceRequest(requestLegacy = Some(Json.toJson(json)), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 0
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 1
    }

    "return no taxCreditSummary attribute when service call to " +
      "'/income/$nino/tax-credits/tax-credits-decision' endpoint throws 401 exception" in {
      val nino = "CS700100A"
      stubHostAndPortGenericConnector
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorFailure(taxCreditDecision(nino), 404)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(new ~(new ~(Some(nino), ConfidenceLevel.L200), Some("creds")))
      val json = """{
                   |  "device": {
                   |    "osVersion": "10.3.3",
                   |    "os": "ios",
                   |    "appVersion": "4.9.0",
                   |    "model": "iPhone8,2"
                   |  },
                   |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                   |}""".stripMargin
      val request = OrchestrationServiceRequest(requestLegacy = Some(Json.toJson(json)), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 0
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 1
    }

    "return taxCreditSummary attribute when service call to " +
      "'/income/tax-credits/submission/state/enabled' endpoint returns a non 200 response" in {
      val nino = "CS700100A"
      stubHostAndPortGenericConnector
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorResponse(taxCreditDecision(nino), TestData.testTaxCreditDecision)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorFailure(taxSummarySubmissionState, 404)
      stubPOSTGenericConnectorResponse(pushRegistration, TestData.testPushReg)
      stubAuthorisationGrantAccess(new ~(new ~(Some(nino), ConfidenceLevel.L200), Some("creds")))
      val json = """{
                   |  "device": {
                   |    "osVersion": "10.3.3",
                   |    "os": "ios",
                   |    "appVersion": "4.9.0",
                   |    "model": "iPhone8,2"
                   |  },
                   |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                   |}""".stripMargin
      val request = OrchestrationServiceRequest(requestLegacy = Some(Json.toJson(json)), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 1
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 1
    }

    "response data is not effected by a failure to execute push-registration service" in {
      val nino = "CS700100A"
      stubHostAndPortGenericConnector
      stubGETGenericConnectorResponse(taxSummary(nino, 2017), TestData.taxSummaryData())
      stubGETGenericConnectorResponse(taxCreditDecision(nino), TestData.testTaxCreditDecision)
      stubGETGenericConnectorResponse(taxCreditSummary(nino), TestData.taxCreditSummaryData)
      stubGETGenericConnectorResponse(taxSummarySubmissionState, TestData.testState)
      stubPOSTGenericConnectorFailure(pushRegistration, 400)
      stubAuthorisationGrantAccess(new ~(new ~(Some(nino), ConfidenceLevel.L200), Some("creds")))
      val json = Json.parse("""{
                   |  "device": {
                   |    "osVersion": "10.3.3",
                   |    "os": "ios",
                   |    "appVersion": "4.9.0",
                   |    "model": "iPhone8,2"
                   |  },
                   |  "token": "cxEVFiqVApc:APA91bFfSsZ38hpJOFKoplI88tp2uSQgf0baE9jL5PENJBoPcWSw7oxXTG9pV47PPrUkiPJM6EgNdgoouQ2KRWx7MaTYyfrPGH21Qn088h6biv8_ZuGG_ZPRIiE9hd959Ccfv1NAZq3b"
                   |}""".stripMargin)
      val request = OrchestrationServiceRequest(requestLegacy = Some(json), None)
      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, 200)
      val response = await(liveOrchestrationService.orchestrate(request, Nino(nino), Some(randomUUID)))
      (response \\ "taxSummary").size shouldBe 1
      (response \\ "taxCreditSummary").size shouldBe 1
      (response \\ "state").size shouldBe 1
      (response \\ "campaigns").size shouldBe 1
    }

  }

}
