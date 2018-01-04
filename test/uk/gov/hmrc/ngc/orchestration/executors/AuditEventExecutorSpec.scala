/*
 * Copyright 2018 HM Revenue & Customs
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

import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Assertion, AsyncWordSpec, Matchers, OptionValues}
import org.slf4j.Logger
import play.api.LoggerLike
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.{Audit, DataEvent}
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.{ExecutionContext, Future}

class AuditEventExecutorSpec extends AsyncWordSpec with Matchers with MockitoSugar with OptionValues {

  private def withFixtures(testCode: (Logger, AuditConnector, AuditEventExecutor) => Future[Assertion]) = {
    val mockSlf4jLogger = mock[Logger]
    when(mockSlf4jLogger.isWarnEnabled).thenReturn(true)
    val logger = new LoggerLike {
      override val logger: Logger = mockSlf4jLogger
    }

    val mockAuditConnector = mock[AuditConnector]

    val auditEventExecutor = AuditEventExecutor(new Audit("test-app", mockAuditConnector), logger)

    when(mockAuditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future successful AuditResult.Success)

    testCode(mockSlf4jLogger, mockAuditConnector, auditEventExecutor)
  }


  "execute" should {
    "Send an audit event with the correct detail when the new JSON format is used" in withFixtures { (mockSlf4jLogger, mockAuditConnector, auditEventExecutor) =>
      val data = Json.parse(
        """
          |{
          |  "auditType": "TCSPayment",
          |  "generatedAt": "2017-09-12T08:35:49.340Z",
          |  "detail": {
          |    "nino": "some-nino",
          |    "someArbitraryDetailName": "someArbitraryDetailValue",
          |    "ctcFrequency": "WEEKLY",
          |    "wtcFrequency": "NONE"
          |  }
          |}
        """.stripMargin)
      val hc = HeaderCarrier()
      auditEventExecutor.execute(None, Some(data), "data-is-used-not-this-nino-arg", None)(hc, executionContext).map { executorResponse =>
        executorResponse.value.failure.value shouldBe false

        val argument = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockAuditConnector).sendEvent(argument.capture())(any[HeaderCarrier], any[ExecutionContext])

        val actualEvent: DataEvent = argument.getValue
        actualEvent.auditType shouldBe "TCSPayment"
        actualEvent.generatedAt shouldBe new DateTime(2017, 9, 12, 8, 35, 49, 340, DateTimeZone.UTC)
        actualEvent.detail.get("nino") shouldBe Some("some-nino")
        actualEvent.detail.get("someArbitraryDetailName") shouldBe Some("someArbitraryDetailValue")
        actualEvent.detail.get("ctcFrequency") shouldBe Some("WEEKLY")
        actualEvent.detail.get("wtcFrequency") shouldBe Some("NONE")

        verify(mockSlf4jLogger, never()).warn(anyString)

        succeed
      }
    }

    "Use the current date & time when generatedAt is omitted" in withFixtures { (mockSlf4jLogger, mockAuditConnector, auditEventExecutor) =>
      val data = Json.parse(
        """
          |{
          |  "auditType": "TCSPayment",
          |  "detail": {
          |    "nino": "some-nino",
          |    "someArbitraryDetailName": "someArbitraryDetailValue",
          |    "ctcFrequency": "WEEKLY",
          |    "wtcFrequency": "NONE"
          |  }
          |}
        """.stripMargin)
      val hc = HeaderCarrier()
      auditEventExecutor.execute(None, Some(data), "data-is-used-not-this-nino-arg", None)(hc, executionContext).map { executorResponse =>
        executorResponse.value.failure.value shouldBe false

        val argument = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockAuditConnector).sendEvent(argument.capture())(any[HeaderCarrier], any[ExecutionContext])

        val actualEvent: DataEvent = argument.getValue
        actualEvent.auditType shouldBe "TCSPayment"
        actualEvent.generatedAt should beCloseToNow
        actualEvent.detail.get("nino") shouldBe Some("some-nino")
        actualEvent.detail.get("someArbitraryDetailName") shouldBe Some("someArbitraryDetailValue")
        actualEvent.detail.get("ctcFrequency") shouldBe Some("WEEKLY")
        actualEvent.detail.get("wtcFrequency") shouldBe Some("NONE")

        verify(mockSlf4jLogger, never()).warn(anyString)

        succeed
      }
    }

    "Use the current date & time when generatedAt and log warning when generatedAt cannot be parsed" in withFixtures { (mockSlf4jLogger, mockAuditConnector, auditEventExecutor) =>
      val data = Json.parse(
        """
          |{
          |  "auditType": "TCSPayment",
          |  "generatedAt": "invalid",
          |  "detail": {
          |    "nino": "some-nino",
          |    "someArbitraryDetailName": "someArbitraryDetailValue",
          |    "ctcFrequency": "WEEKLY",
          |    "wtcFrequency": "NONE"
          |  }
          |}
        """.stripMargin)
      val hc = HeaderCarrier()
      auditEventExecutor.execute(None, Some(data), "data-is-used-not-this-nino-arg", Some("myJourneyId"))(hc, executionContext).map { executorResponse =>
        executorResponse.value.failure.value shouldBe false

        val argument = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockAuditConnector).sendEvent(argument.capture())(any[HeaderCarrier], any[ExecutionContext])

        val actualEvent: DataEvent = argument.getValue
        actualEvent.auditType shouldBe "TCSPayment"
        actualEvent.generatedAt should beCloseToNow
        actualEvent.detail.get("nino") shouldBe Some("some-nino")
        actualEvent.detail.get("someArbitraryDetailName") shouldBe Some("someArbitraryDetailValue")
        actualEvent.detail.get("ctcFrequency") shouldBe Some("WEEKLY")
        actualEvent.detail.get("wtcFrequency") shouldBe Some("NONE")

        verify(mockSlf4jLogger).warn(s"""Couldn't parse generatedAt timestamp "invalid", defaulting to now for audit event ${actualEvent.eventId}""")

        succeed
      }
    }

    "Support old JSON format" in withFixtures { (mockSlf4jLogger, mockAuditConnector, auditEventExecutor) =>
      val data = Json.parse(
        """
          |{
          |  "auditType": "TCSPayment",
          |  "nino": "some-nino"
          |}
        """.stripMargin)
      val hc = HeaderCarrier()
      auditEventExecutor.execute(None, Some(data), "data-is-used-not-this-nino-arg", None)(hc, executionContext).map { executorResponse =>
        executorResponse.value.failure.value shouldBe false

        val argument = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockAuditConnector).sendEvent(argument.capture())(any[HeaderCarrier], any[ExecutionContext])

        val actualEvent: DataEvent = argument.getValue
        actualEvent.auditType shouldBe "TCSPayment"
        actualEvent.detail.get("nino") shouldBe Some("some-nino")

        verify(mockSlf4jLogger, never()).warn(anyString)

        succeed
      }
    }
  }

  private val beCloseToNow = new Matcher[DateTime] {
    override def apply(left: DateTime) = MatchResult(
      math.abs(left.toInstant.getMillis - DateTimeUtils.now.toInstant.getMillis) < 10000L,
      s"""Date $left was not close to now""",
      s"""Date $left was close to now"""
    )
  }
}
