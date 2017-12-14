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

import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncWordSpec, Matchers, OptionValues}
import play.api.libs.json.{JsObject, JsString}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.services.{Result, TaxCreditsRenewals}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TaxCreditsRenewalsSpec extends AsyncWordSpec with Matchers with MockitoSugar with OptionValues{
  private val connector = mock[GenericConnector]
  private val executor = TaxCreditsRenewals(connector, Some("journeyId"))
  private val serviceName = "personal-income"
  private val url = "/income/tax-credits/submission/state/enabled?journeyId=journeyId"

  private implicit val hc = HeaderCarrier()

  "execute" should {
    "return submissionsState open during the submissions period" in {
      ensureStateReturnedByPersonalIncomeIsPassedThrough("open")
    }

    "return to submissionsState check_status_only during the check-only period" in {
      ensureStateReturnedByPersonalIncomeIsPassedThrough("check_status_only")
    }

    "return submissionsState closed during the closed period" in {
      ensureStateReturnedByPersonalIncomeIsPassedThrough("closed")
    }

    "return to submissionsState shuttered when the service is shuttered" in {
      ensureStateReturnedByPersonalIncomeIsPassedThrough("shuttered")
    }

    "return submissionsState error when personal-income is unavailable" in {
      when(connector.doGet( serviceName, url, hc)).thenReturn(Future failed new RuntimeException)
      verifyResult("error")
    }
  }

  private def verifyResult(state : String) = {
    executor.execute("nino", 2017).map { result =>
      result shouldBe Some(Result("taxCreditRenewals", JsObject(Seq("submissionsState" -> JsString(state)))))
      succeed
    }
  }

  private def ensureStateReturnedByPersonalIncomeIsPassedThrough(state: String) = {
    when(connector.doGet(serviceName, url, hc)).thenReturn(Future successful JsObject(Seq("submissionsState" -> JsString(state))))
    verifyResult(state)
  }
}
