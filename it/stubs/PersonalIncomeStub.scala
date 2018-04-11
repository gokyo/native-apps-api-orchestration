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

package stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import stubs.StubShortcuts._

object PersonalIncomeStub {
  // Returns a small extract of the JSON that the real personal-income service would return.
  // The full JSON is not important here because all this service does is passes it through unmodified.
  def taxSummaryJson(nino: String): String =
    s"""
       |{
       |  "taxSummaryDetails": {
       |    "nino": "$nino"
       |  },
       |  "estimatedIncomeWrapper": {
       |  }
       |}
        """.stripMargin

  def taxSummarySucceeds(nino : String, year: String, taxSummaryJson: String) : Unit = {
    stubGetSuccess(s"/income/$nino/tax-summary/$year", taxSummaryJson)
  }

  val taxCreditSummaryJson: String =
    """
      |{
      |  "paymentSummary": {
      |    "workingTaxCredit": {
      |      "paymentSeq": [
      |        {
      |          "amount": 160.45,
      |          "paymentDate": 1508367600000,
      |          "oneOffPayment": false
      |        }
      |      ]
      |    }
      |  }
      |}
    """.
      stripMargin

  def taxCreditSummarySucceeds(nino: String, responseJson: String) : Unit = {
    stubGetSuccess(s"/income/$nino/tax-credits/tax-credits-summary", responseJson)
  }

  def taxCreditsDecisionSucceeds(nino: String, showData: Boolean = true): Unit = {
    val response =
      s"""
        |{"showData":$showData}
      """.stripMargin
    stubGetSuccess(s"/income/$nino/tax-credits/tax-credits-decision", response)
  }

  def taxCreditsSubmissionStateIsEnabled(): Unit = {
    val response =
      s"""
        |{
        |  "submissionState": true,
        |  "submissionsState": "open"
        |}
      """.stripMargin
    stubGetSuccess("/income/tax-credits/submission/state/enabled", response)
  }

  def claimantDetailsAreFound(nino:String, barcodeReference:String, journeyId:String) = {
      val json =
        s"""{
           |  "references": [
           |    {
           |      "household": {
           |        "barcodeReference": "$barcodeReference",
           |        "applicationID": "198765432134567",
           |        "applicant1": {
           |          "nino": "$nino",
           |          "title": "MR",
           |          "firstForename": "JOHN",
           |          "secondForename": "",
           |          "surname": "DENSMORE"
           |        },
           |        "householdEndReason": ""
           |      },
           |      "renewal": {
           |        "awardStartDate": "12/10/2030",
           |        "awardEndDate": "12/10/2010",
           |        "renewalStatus": "NOT_SUBMITTED",
           |        "renewalNoticeIssuedDate": "12/10/2030",
           |        "renewalNoticeFirstSpecifiedDate": "12/10/2010",
           |        "renewalFormType": "D"
           |      }
           |    },
           |    {
           |      "household": {
           |        "barcodeReference": "000000000000000",
           |        "applicationID": "198765432134567",
           |        "applicant1": {
           |          "nino": "$nino",
           |          "title": "MR",
           |          "firstForename": "JOHN",
           |          "secondForename": "",
           |          "surname": "DENSMORE"
           |        },
           |        "householdEndReason": ""
           |      },
           |      "renewal": {
           |        "awardStartDate": "12/10/2030",
           |        "awardEndDate": "12/10/2010",
           |        "renewalStatus": "AWAITING_BARCODE",
           |        "renewalNoticeIssuedDate": "12/10/2030",
           |        "renewalNoticeFirstSpecifiedDate": "12/10/2010"
           |      }
           |    }
           |  ]
           |}
           |""".stripMargin

      stubFor(get(urlEqualTo(s"/income/$nino/tax-credits/full-claimant-details?journeyId=$journeyId"))
        .willReturn(aResponse().withStatus(200).withBody(json)))
  }

  def claimantDetailsFails(nino:String, journeyId:String) = {
    stubFor(get(urlPathEqualTo(s"/income/$nino/tax-credits/full-claimant-details?journeyId=$journeyId")).willReturn(
      aResponse().withStatus(500).withBody("""{ code":"error", "message":"123" }""")))
  }
}
