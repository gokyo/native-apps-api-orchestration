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

package uk.gov.hmrc.ngc.orchestration.executors

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.{Audit, AuditEvent, DataEvent}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.{global => ec}
import scala.concurrent.{ExecutionContext, Future}

class AuditEventExecutorSpec extends UnitSpec with MockitoSugar {

  "execute" should {
    "Send an audit event with the correct details when the new JSON format is used" in {
      val mockAuditConnector = mock[AuditConnector]

      when(mockAuditConnector.sendEvent(any[AuditEvent])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future successful AuditResult.Success)
      val auditEventExecutor = AuditEventExecutor(new Audit("test-app", mockAuditConnector))

      val data = Json.parse(
        """
          |{
          |  "auditType": "TCSPayment",
          |  "details": {
          |    "nino": "some-nino",
          |    "someArbitraryDetailName": "someArbitraryDetailValue",
          |    "ctcFrequency": "WEEKLY",
          |    "wtcFrequency": "NONE"
          |  }
          |}
        """.stripMargin)
      val hc = HeaderCarrier()
      auditEventExecutor.execute(None, Some(data), "data-is-used-not-this-nino-arg", None)(hc, ec)

      val argument = ArgumentCaptor.forClass(classOf[AuditEvent])
      verify(mockAuditConnector).sendEvent(argument.capture())(any[HeaderCarrier], any[ExecutionContext])

      val actualAuditEvent: AuditEvent = argument.getValue
      actualAuditEvent match {
        case actualDataEvent: DataEvent =>
          actualDataEvent.auditType shouldBe "TCSPayment"
          actualDataEvent.detail.get("nino") shouldBe Some("some-nino")
          actualDataEvent.detail.get("someArbitraryDetailName") shouldBe Some("someArbitraryDetailValue")
          actualDataEvent.detail.get("ctcFrequency") shouldBe Some("WEEKLY")
          actualDataEvent.detail.get("wtcFrequency") shouldBe Some("NONE")
        case _ =>
          fail(s"audited event was not a DataEvent: $actualAuditEvent")
      }
    }

    "Support old JSON format" in {
      val mockAuditConnector = mock[AuditConnector]

      when(mockAuditConnector.sendEvent(any[AuditEvent])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future successful AuditResult.Success)
      val auditEventExecutor = AuditEventExecutor(new Audit("test-app", mockAuditConnector))

      val data = Json.parse(
        """
          |{
          |  "auditType": "TCSPayment",
          |  "nino": "some-nino"
          |}
        """.stripMargin)
      val hc = HeaderCarrier()
      auditEventExecutor.execute(None, Some(data), "data-is-used-not-this-nino-arg", None)(hc, ec)

      val argument = ArgumentCaptor.forClass(classOf[AuditEvent])
      verify(mockAuditConnector).sendEvent(argument.capture())(any[HeaderCarrier], any[ExecutionContext])

      val actualAuditEvent: AuditEvent = argument.getValue
      actualAuditEvent match {
        case actualDataEvent: DataEvent =>
          actualDataEvent.auditType shouldBe "TCSPayment"
          actualDataEvent.detail.get("nino") shouldBe Some("some-nino")
        case _ =>
          fail(s"audited event was not a DataEvent: $actualAuditEvent")
      }
    }
  }
}
