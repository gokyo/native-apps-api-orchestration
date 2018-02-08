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

package uk.gov.hmrc.ngc.orchestration.controllers

import play.api.libs.json._


object TestData {

  def upgradeRequired(upgradeRequired: Boolean) : JsValue = Json.parse(s"""{"upgrade": $upgradeRequired}""")

  def responseTicket : JsValue = Json.parse(s"""{"ticket_id": 1980683879}""")

  val taxCreditRenewalsStateOpen: JsValue = Json.parse("""{"submissionState":true, "submissionsState":"open"}""")
  val testTaxCreditDecision: JsValue = Json.parse("""{"showData":true}""")
  val helpToSaveStartupResponse: JsValue = Json.obj("thisIs" -> "some help to save data")

  val testPushReg: JsValue = JsNull

  val pollResponse: JsValue = Json.obj("status" -> Json.parse("""{"code":"poll"}"""))

  def taxSummaryData(additional:Option[String]=None) : JsValue = Json.parse(
    s"""{
      |    "taxSummaryDetails": {
      |      "nino": "CS700100A",
      |      "version": 154,
      |      "increasesTax": {
      |        "incomes": {
      |          "taxCodeIncomes": {
      |            "employments": {
      |              "taxCodeIncomes": [
      |                {
      |                  "name": "Sainsburys",
      |                  "taxCode": "1150L",
      |                  "employmentId": 2,
      |                  "employmentPayeRef": "BT456",
      |                  "employmentType": 1,
      |                  "incomeType": 0,
      |                  "employmentStatus": 1,
      |                  "tax": {
      |                    "totalIncome": 18900,
      |                    "totalTaxableIncome": 18900,
      |                    "totalTax": 1480,
      |                    "potentialUnderpayment": 0,
      |                    "taxBands": [
      |                      {
      |                        "income": 7400,
      |                        "tax": 1480,
      |                        "lowerBand": 0,
      |                        "upperBand": 32000,
      |                        "rate": 20
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 32000,
      |                        "upperBand": 150000,
      |                        "rate": 40
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 150000,
      |                        "upperBand": 0,
      |                        "rate": 45
      |                      }
      |                    ],
      |                    "allowReliefDeducts": 179
      |                  },
      |                  "worksNumber": "1",
      |                  "jobTitle": " ",
      |                  "startDate": "2008-04-06",
      |                  "income": 18900,
      |                  "otherIncomeSourceIndicator": false,
      |                  "isEditable": true,
      |                  "isLive": true,
      |                  "isOccupationalPension": false,
      |                  "isPrimary": true
      |                }
      |              ],
      |              "totalIncome": 18900,
      |              "totalTax": 1480,
      |              "totalTaxableIncome": 18900
      |            },
      |            "hasDuplicateEmploymentNames": false,
      |            "totalIncome": 18900,
      |            "totalTaxableIncome": 18900,
      |            "totalTax": 1480
      |          },
      |          "noneTaxCodeIncomes": {
      |            "totalIncome": 0
      |          },
      |          "total": 18900
      |        },
      |        "total": 18900
      |      },
      |      "decreasesTax": {
      |        "personalAllowance": 11500,
      |        "personalAllowanceSourceAmount": 11500,
      |        "paTapered": false,
      |        "total": 11500
      |      },
      |      "totalLiability": {
      |        "nonSavings": {
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 18900,
      |          "totalTax": 1480,
      |          "taxBands": [
      |            {
      |              "income": 7400,
      |              "tax": 1480,
      |              "lowerBand": 0,
      |              "upperBand": 32000,
      |              "rate": 20
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 40
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 45
      |            }
      |          ],
      |          "allowReliefDeducts": 11500
      |        },
      |        "mergedIncomes": {
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 18900,
      |          "totalTax": 1480,
      |          "taxBands": [
      |            {
      |              "income": 93,
      |              "tax": 0,
      |              "lowerBand": 0,
      |              "upperBand": 5000,
      |              "rate": 0
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 5000,
      |              "upperBand": 32000,
      |              "rate": 7.5
      |            },
      |            {
      |              "income": 7400,
      |              "tax": 1480,
      |              "lowerBand": 0,
      |              "upperBand": 32000,
      |              "rate": 20
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 32.5
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 38.1
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 40
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 45
      |            }
      |          ],
      |          "allowReliefDeducts": 11500
      |        },
      |        "totalLiability": 18900,
      |        "totalTax": 1480,
      |        "totalTaxOnIncome": 1480,
      |        "underpaymentPreviousYear": 0,
      |        "outstandingDebt": 0,
      |        "childBenefitAmount": 0,
      |        "childBenefitTaxDue": 0,
      |        "liabilityReductions": {
      |          "enterpriseInvestmentSchemeRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "concessionalRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "maintenancePayments": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "doubleTaxationRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          }
      |        },
      |        "liabilityAdditions": {
      |          "excessGiftAidTax": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "excessWidowsAndOrphans": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "pensionPaymentsAdjustment": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          }
      |        }
      |      },
      |      "extensionReliefs": {
      |        "giftAid": {
      |          "sourceAmount": 0,
      |          "reliefAmount": 0
      |        },
      |        "personalPension": {
      |          "sourceAmount": 0,
      |          "reliefAmount": 0
      |        }
      |      },
      |      "taxCodeDetails": {
      |        "employment": [
      |          {
      |            "id": 2,
      |            "name": "Sainsburys",
      |            "taxCode": "1150L"
      |          }
      |        ],
      |        "taxCode": [
      |          {
      |            "taxCode": "L"
      |          }
      |        ],
      |        "taxCodeDescriptions": [
      |          {
      |            "taxCode": "1150L",
      |            "name": "Sainsburys",
      |            "taxCodeDescriptors": [
      |              {
      |                "taxCode": "L"
      |              }
      |            ]
      |          }
      |        ],
      |        "deductions": [
      |
      |        ],
      |        "allowances": [
      |          {
      |            "description": "Tax Free Amount",
      |            "amount": 11500,
      |            "componentType": 0
      |          }
      |        ],
      |        "splitAllowances": false,
      |        "total": 0
      |      }
      |    },
      |    "baseViewModel": {
      |      "estimatedIncomeTax": 1480,
      |      "taxableIncome": 18900,
      |      "taxFree": 11500,
      |      "personalAllowance": 11500,
      |      "hasTamc": false,
      |      "taxCodesList": [
      |        "1150L"
      |      ],
      |      "hasChanges": false
      |    },
      |    "estimatedIncomeWrapper": {
      |      "estimatedIncome": {
      |        "increasesTax": true,
      |        "incomeTaxEstimate": 1480,
      |        "incomeEstimate": 18900,
      |        "taxFreeEstimate": 11500,
      |        "taxRelief": false,
      |        "taxCodes": [
      |          "1150L"
      |        ],
      |        "potentialUnderpayment": false,
      |        "additionalTaxTable": [
      |
      |        ],
      |        "additionalTaxTableTotal": "0.00",
      |        "reductionsTable": [
      |
      |        ],
      |        "reductionsTableTotal": "-0.00",
      |        "graph": {
      |          "id": "taxGraph",
      |          "bands": [
      |            {
      |              "colour": "TaxFree",
      |              "barPercentage": 60.85,
      |              "tablePercentage": "0",
      |              "income": 11500,
      |              "tax": 0
      |            },
      |            {
      |              "colour": "Band1",
      |              "barPercentage": 39.15,
      |              "tablePercentage": "20",
      |              "income": 18900,
      |              "tax": 1480
      |            }
      |          ],
      |          "minBand": 0,
      |          "nextBand": 18900,
      |          "incomeTotal": 18900,
      |          "incomeAsPercentage": 100,
      |          "taxTotal": 1480
      |        },
      |        "hasChanges": false
      |      }
      |    },
      |    "taxableIncome": {
      |      "taxFreeAmount": 11500,
      |      "incomeTax": 1480,
      |      "income": 18900,
      |      "taxCodeList": [
      |        "1150L"
      |      ],
      |      "increasesTax": {
      |        "incomes": {
      |          "taxCodeIncomes": {
      |            "employments": {
      |              "taxCodeIncomes": [
      |                {
      |                  "name": "Sainsburys",
      |                  "taxCode": "1150L",
      |                  "employmentId": 2,
      |                  "employmentPayeRef": "BT456",
      |                  "employmentType": 1,
      |                  "incomeType": 0,
      |                  "employmentStatus": 1,
      |                  "tax": {
      |                    "totalIncome": 18900,
      |                    "totalTaxableIncome": 18900,
      |                    "totalTax": 1480,
      |                    "potentialUnderpayment": 0,
      |                    "taxBands": [
      |                      {
      |                        "income": 7400,
      |                        "tax": 1480,
      |                        "lowerBand": 0,
      |                        "upperBand": 32000,
      |                        "rate": 20
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 32000,
      |                        "upperBand": 150000,
      |                        "rate": 40
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 150000,
      |                        "upperBand": 0,
      |                        "rate": 45
      |                      }
      |                    ],
      |                    "allowReliefDeducts": 179
      |                  },
      |                  "worksNumber": "1",
      |                  "jobTitle": " ",
      |                  "startDate": "2008-04-06",
      |                  "income": 18900,
      |                  "otherIncomeSourceIndicator": false,
      |                  "isEditable": true,
      |                  "isLive": true,
      |                  "isOccupationalPension": false,
      |                  "isPrimary": true
      |                }
      |              ],
      |              "totalIncome": 18900,
      |              "totalTax": 1480,
      |              "totalTaxableIncome": 18900
      |            },
      |            "hasDuplicateEmploymentNames": false,
      |            "totalIncome": 18900,
      |            "totalTaxableIncome": 18900,
      |            "totalTax": 1480
      |          },
      |          "noneTaxCodeIncomes": {
      |            "totalIncome": 0
      |          },
      |          "total": 18900
      |        },
      |        "total": 18900
      |      },
      |      "employmentPension": {
      |        "taxCodeIncomes": {
      |          "employments": {
      |            "taxCodeIncomes": [
      |              {
      |                "name": "Sainsburys",
      |                "taxCode": "1150L",
      |                "employmentId": 2,
      |                "employmentPayeRef": "BT456",
      |                "employmentType": 1,
      |                "incomeType": 0,
      |                "employmentStatus": 1,
      |                "tax": {
      |                  "totalIncome": 18900,
      |                  "totalTaxableIncome": 18900,
      |                  "totalTax": 1480,
      |                  "potentialUnderpayment": 0,
      |                  "taxBands": [
      |                    {
      |                      "income": 7400,
      |                      "tax": 1480,
      |                      "lowerBand": 0,
      |                      "upperBand": 32000,
      |                      "rate": 20
      |                    },
      |                    {
      |                      "income": 0,
      |                      "tax": 0,
      |                      "lowerBand": 32000,
      |                      "upperBand": 150000,
      |                      "rate": 40
      |                    },
      |                    {
      |                      "income": 0,
      |                      "tax": 0,
      |                      "lowerBand": 150000,
      |                      "upperBand": 0,
      |                      "rate": 45
      |                    }
      |                  ],
      |                  "allowReliefDeducts": 179
      |                },
      |                "worksNumber": "1",
      |                "jobTitle": " ",
      |                "startDate": "2008-04-06",
      |                "income": 7354,
      |                "otherIncomeSourceIndicator": false,
      |                "isEditable": true,
      |                "isLive": true,
      |                "isOccupationalPension": false,
      |                "isPrimary": true
      |              }
      |            ],
      |            "totalIncome": 18900,
      |            "totalTax": 1480,
      |            "totalTaxableIncome": 18900
      |          },
      |          "hasDuplicateEmploymentNames": false,
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 18900,
      |          "totalTax": 1480
      |        },
      |        "totalEmploymentPensionAmt": 18900,
      |        "hasEmployment": true,
      |        "isOccupationalPension": false
      |      },
      |      "investmentIncomeData": [
      |
      |      ],
      |      "investmentIncomeTotal": 0,
      |      "otherIncomeData": [
      |
      |      ],
      |      "otherIncomeTotal": 0,
      |      "benefitsData": [
      |
      |      ],
      |      "benefitsTotal": 0,
      |      "taxableBenefitsData": [
      |
      |      ],
      |      "taxableBenefitsTotal": 0,
      |      "hasChanges": false
      |    }
      |    ${additional.fold(""){id => s""","ASYNC_TEST_ID":"$id""""}}
      |}
    """.stripMargin)

  val statusThrottle: JsValue = Json.obj("status" -> Json.parse("""{"code": "throttle"}"""))

  val taxCreditSummaryData: JsValue = Json.parse(
    """
      |{
      |  "paymentSummary": {
      |    "workingTaxCredit": {
      |      "paymentSeq": [
      |        {
      |          "amount": 86.63,
      |          "paymentDate": 1437004800000,
      |          "oneOffPayment": false
      |        }
      |      ],
      |      "paymentFrequency": "weekly"
      |    },
      |    "childTaxCredit": {
      |      "paymentSeq": [
      |        {
      |          "amount": 51.76,
      |          "paymentDate": 1437004800000,
      |          "oneOffPayment": false
      |        }
      |      ],
      |      "paymentFrequency": "weekly"
      |    },
      |    "paymentEnabled": true,
      |    "totalsByDate": [
      |      {
      |        "amount": 138.39,
      |        "paymentDate": 1437004800000
      |      }
      |    ]
      |  },
      |    "personalDetails": {
      |      "forename": "Nuala",
      |      "surname": "O'Shea",
      |      "nino": "CS700100A",
      |      "address": {
      |        "addressLine1": "19 Bushey Hall Road",
      |        "addressLine2": "Bushey",
      |        "addressLine3": "Watford",
      |        "addressLine4": "Hertfordshire",
      |        "postCode": "WD23 2EE"
      |      }
      |    },
      |    "partnerDetails": {
      |      "forename": "Frederick",
      |      "otherForenames": "Tarquin",
      |      "surname": "Hunter-Smith",
      |      "nino": "CS700100A",
      |      "address": {
      |        "addressLine1": "19 Bushey Hall Road",
      |        "addressLine2": "Bushey",
      |        "addressLine3": "Watford",
      |        "addressLine4": "Hertfordshire",
      |        "postCode": "WD23 2EE"
      |      }
      |    },
      |    "children": {
      |      "child": [
      |        {
      |          "firstNames": "Sarah",
      |          "surname": "Smith",
      |          "dateOfBirth": 936057600000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        },
      |        {
      |          "firstNames": "Joseph",
      |          "surname": "Smith",
      |          "dateOfBirth": 884304000000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        },
      |        {
      |          "firstNames": "Mary",
      |          "surname": "Smith",
      |          "dateOfBirth": 852768000000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        }
      |      ]
      |    },
      |    "showData": true
      |  }
    """.stripMargin)

  val sandboxStartupResponse: JsValue = Json.obj("status" -> Json.parse("""{"code":"poll"}"""))

  val sandboxPollResponse: String =
    """
      |{
      |  "taxSummary": {
      |    "taxSummaryDetails": {
      |      "nino": "CS700100A",
      |      "version": 154,
      |      "increasesTax": {
      |        "incomes": {
      |          "taxCodeIncomes": {
      |            "employments": {
      |              "taxCodeIncomes": [
      |                {
      |                  "name": "Sainsburys",
      |                  "taxCode": "1150L",
      |                  "employmentId": 2,
      |                  "employmentPayeRef": "BT456",
      |                  "employmentType": 1,
      |                  "incomeType": 0,
      |                  "employmentStatus": 1,
      |                  "tax": {
      |                    "totalIncome": 18900,
      |                    "totalTaxableIncome": 18900,
      |                    "totalTax": 1480,
      |                    "potentialUnderpayment": 0,
      |                    "taxBands": [
      |                      {
      |                        "income": 7400,
      |                        "tax": 1480,
      |                        "lowerBand": 0,
      |                        "upperBand": 32000,
      |                        "rate": 20
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 32000,
      |                        "upperBand": 150000,
      |                        "rate": 40
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 150000,
      |                        "upperBand": 0,
      |                        "rate": 45
      |                      }
      |                    ],
      |                    "allowReliefDeducts": 179
      |                  },
      |                  "worksNumber": "1",
      |                  "jobTitle": " ",
      |                  "startDate": "2008-04-06",
      |                  "income": 18900,
      |                  "otherIncomeSourceIndicator": false,
      |                  "isEditable": true,
      |                  "isLive": true,
      |                  "isOccupationalPension": false,
      |                  "isPrimary": true
      |                }
      |              ],
      |              "totalIncome": 18900,
      |              "totalTax": 1480,
      |              "totalTaxableIncome": 18900
      |            },
      |            "hasDuplicateEmploymentNames": false,
      |            "totalIncome": 18900,
      |            "totalTaxableIncome": 18900,
      |            "totalTax": 1480
      |          },
      |          "noneTaxCodeIncomes": {
      |            "totalIncome": 0
      |          },
      |          "total": 18900
      |        },
      |        "total": 18900
      |      },
      |      "decreasesTax": {
      |        "personalAllowance": 11500,
      |        "personalAllowanceSourceAmount": 11500,
      |        "paTapered": false,
      |        "total": 11500
      |      },
      |      "totalLiability": {
      |        "nonSavings": {
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 18900,
      |          "totalTax": 1480,
      |          "taxBands": [
      |            {
      |              "income": 7400,
      |              "tax": 1480,
      |              "lowerBand": 0,
      |              "upperBand": 32000,
      |              "rate": 20
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 40
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 45
      |            }
      |          ],
      |          "allowReliefDeducts": 11500
      |        },
      |        "mergedIncomes": {
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 18900,
      |          "totalTax": 1480,
      |          "taxBands": [
      |            {
      |              "income": 93,
      |              "tax": 0,
      |              "lowerBand": 0,
      |              "upperBand": 5000,
      |              "rate": 0
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 5000,
      |              "upperBand": 32000,
      |              "rate": 7.5
      |            },
      |            {
      |              "income": 7400,
      |              "tax": 1480,
      |              "lowerBand": 0,
      |              "upperBand": 32000,
      |              "rate": 20
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 32.5
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 38.1
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 40
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 45
      |            }
      |          ],
      |          "allowReliefDeducts": 11500
      |        },
      |        "totalLiability": 18900,
      |        "totalTax": 1480,
      |        "totalTaxOnIncome": 1480,
      |        "underpaymentPreviousYear": 0,
      |        "outstandingDebt": 0,
      |        "childBenefitAmount": 0,
      |        "childBenefitTaxDue": 0,
      |        "liabilityReductions": {
      |          "enterpriseInvestmentSchemeRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "concessionalRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "maintenancePayments": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "doubleTaxationRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          }
      |        },
      |        "liabilityAdditions": {
      |          "excessGiftAidTax": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "excessWidowsAndOrphans": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "pensionPaymentsAdjustment": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          }
      |        }
      |      },
      |      "extensionReliefs": {
      |        "giftAid": {
      |          "sourceAmount": 0,
      |          "reliefAmount": 0
      |        },
      |        "personalPension": {
      |          "sourceAmount": 0,
      |          "reliefAmount": 0
      |        }
      |      },
      |      "taxCodeDetails": {
      |        "employment": [
      |          {
      |            "id": 2,
      |            "name": "Sainsburys",
      |            "taxCode": "1150L"
      |          }
      |        ],
      |        "taxCode": [
      |          {
      |            "taxCode": "L"
      |          }
      |        ],
      |        "taxCodeDescriptions": [
      |          {
      |            "taxCode": "1150L",
      |            "name": "Sainsburys",
      |            "taxCodeDescriptors": [
      |              {
      |                "taxCode": "L"
      |              }
      |            ]
      |          }
      |        ],
      |        "deductions": [
      |
      |        ],
      |        "allowances": [
      |          {
      |            "description": "Tax Free Amount",
      |            "amount": 11500,
      |            "componentType": 0
      |          }
      |        ],
      |        "splitAllowances": false,
      |        "total": 0
      |      }
      |    },
      |    "baseViewModel": {
      |      "estimatedIncomeTax": 1480,
      |      "taxableIncome": 18900,
      |      "taxFree": 11500,
      |      "personalAllowance": 11500,
      |      "hasTamc": false,
      |      "taxCodesList": [
      |        "1150L"
      |      ],
      |      "hasChanges": false
      |    },
      |    "estimatedIncomeWrapper": {
      |      "estimatedIncome": {
      |        "increasesTax": true,
      |        "incomeTaxEstimate": 1480,
      |        "incomeEstimate": 18900,
      |        "taxFreeEstimate": 11500,
      |        "taxRelief": false,
      |        "taxCodes": [
      |          "1150L"
      |        ],
      |        "potentialUnderpayment": false,
      |        "additionalTaxTable": [
      |
      |        ],
      |        "additionalTaxTableTotal": "0.00",
      |        "reductionsTable": [
      |
      |        ],
      |        "reductionsTableTotal": "-0.00",
      |        "graph": {
      |          "id": "taxGraph",
      |          "bands": [
      |            {
      |              "colour": "TaxFree",
      |              "barPercentage": 60.85,
      |              "tablePercentage": "0",
      |              "income": 11500,
      |              "tax": 0
      |            },
      |            {
      |              "colour": "Band1",
      |              "barPercentage": 39.15,
      |              "tablePercentage": "20",
      |              "income": 7400,
      |              "tax": 1480
      |            }
      |          ],
      |          "minBand": 0,
      |          "nextBand": 18900,
      |          "incomeTotal": 18900,
      |          "incomeAsPercentage": 100,
      |          "taxTotal": 1480
      |        },
      |        "hasChanges": false
      |      }
      |    },
      |    "taxableIncome": {
      |      "taxFreeAmount": 11500,
      |      "incomeTax": 1480,
      |      "income": 18900,
      |      "taxCodeList": [
      |        "1150L"
      |      ],
      |      "increasesTax": {
      |        "incomes": {
      |          "taxCodeIncomes": {
      |            "employments": {
      |              "taxCodeIncomes": [
      |                {
      |                  "name": "Sainsburys",
      |                  "taxCode": "1150L",
      |                  "employmentId": 2,
      |                  "employmentPayeRef": "BT456",
      |                  "employmentType": 1,
      |                  "incomeType": 0,
      |                  "employmentStatus": 1,
      |                  "tax": {
      |                    "totalIncome": 18900,
      |                    "totalTaxableIncome": 18900,
      |                    "totalTax": 1480,
      |                    "potentialUnderpayment": 0,
      |                    "taxBands": [
      |                      {
      |                        "income": 7400,
      |                        "tax": 1480,
      |                        "lowerBand": 0,
      |                        "upperBand": 32000,
      |                        "rate": 20
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 32000,
      |                        "upperBand": 150000,
      |                        "rate": 40
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 150000,
      |                        "upperBand": 0,
      |                        "rate": 45
      |                      }
      |                    ],
      |                    "allowReliefDeducts": 179
      |                  },
      |                  "worksNumber": "1",
      |                  "jobTitle": " ",
      |                  "startDate": "2008-04-06",
      |                  "income": 18900,
      |                  "otherIncomeSourceIndicator": false,
      |                  "isEditable": true,
      |                  "isLive": true,
      |                  "isOccupationalPension": false,
      |                  "isPrimary": true
      |                }
      |              ],
      |              "totalIncome": 18900,
      |              "totalTax": 1480,
      |              "totalTaxableIncome": 18900
      |            },
      |            "hasDuplicateEmploymentNames": false,
      |            "totalIncome": 18900,
      |            "totalTaxableIncome": 18900,
      |            "totalTax": 1480
      |          },
      |          "noneTaxCodeIncomes": {
      |            "totalIncome": 0
      |          },
      |          "total": 18900
      |        },
      |        "total": 18900
      |      },
      |      "employmentPension": {
      |        "taxCodeIncomes": {
      |          "employments": {
      |            "taxCodeIncomes": [
      |              {
      |                "name": "Sainsburys",
      |                "taxCode": "1150L",
      |                "employmentId": 2,
      |                "employmentPayeRef": "BT456",
      |                "employmentType": 1,
      |                "incomeType": 0,
      |                "employmentStatus": 1,
      |                "tax": {
      |                  "totalIncome": 18900,
      |                  "totalTaxableIncome": 18900,
      |                  "totalTax": 1480,
      |                  "potentialUnderpayment": 0,
      |                  "taxBands": [
      |                    {
      |                      "income": 7400,
      |                      "tax": 1480,
      |                      "lowerBand": 0,
      |                      "upperBand": 32000,
      |                      "rate": 20
      |                    },
      |                    {
      |                      "income": 0,
      |                      "tax": 0,
      |                      "lowerBand": 32000,
      |                      "upperBand": 150000,
      |                      "rate": 40
      |                    },
      |                    {
      |                      "income": 0,
      |                      "tax": 0,
      |                      "lowerBand": 150000,
      |                      "upperBand": 0,
      |                      "rate": 45
      |                    }
      |                  ],
      |                  "allowReliefDeducts": 179
      |                },
      |                "worksNumber": "1",
      |                "jobTitle": " ",
      |                "startDate": "2008-04-06",
      |                "income": 7354,
      |                "otherIncomeSourceIndicator": false,
      |                "isEditable": true,
      |                "isLive": true,
      |                "isOccupationalPension": false,
      |                "isPrimary": true
      |              }
      |            ],
      |            "totalIncome": 18900,
      |            "totalTax": 1480,
      |            "totalTaxableIncome": 18900
      |          },
      |          "hasDuplicateEmploymentNames": false,
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 18900,
      |          "totalTax": 1480
      |        },
      |        "totalEmploymentPensionAmt": 18900,
      |        "hasEmployment": true,
      |        "isOccupationalPension": false
      |      },
      |      "investmentIncomeData": [
      |
      |      ],
      |      "investmentIncomeTotal": 0,
      |      "otherIncomeData": [
      |
      |      ],
      |      "otherIncomeTotal": 0,
      |      "benefitsData": [
      |
      |      ],
      |      "benefitsTotal": 0,
      |      "taxableBenefitsData": [
      |
      |      ],
      |      "taxableBenefitsTotal": 0,
      |      "hasChanges": false
      |    }
      |  },
      |  "taxCreditSummary": {
      |    "paymentSummary": {
      |    "workingTaxCredit": {
      |      "paymentSeq": [
      |        {
      |          "amount": 80.29,
      |          "paymentDate": date1,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 80.29,
      |          "paymentDate": date2,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 80.29,
      |          "paymentDate": date3,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 80.29,
      |          "paymentDate": date4,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 80.29,
      |          "paymentDate": date5,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 80.29,
      |          "paymentDate": date6,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 80.29,
      |          "paymentDate": date7,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 80.29,
      |          "paymentDate": date8,
      |          "oneOffPayment": false
      |        }
      |      ],
      |      "paymentFrequency": "weekly",
      |      "previousPaymentSeq": [
      |        {
      |          "amount": 100,
      |          "paymentDate": previousDate1,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 100,
      |          "paymentDate": previousDate2,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 170.31,
      |          "paymentDate": previousDate3,
      |          "oneOffPayment": true
      |        }
      |      ]
      |    },
      |    "childTaxCredit": {
      |      "paymentSeq": [
      |        {
      |          "amount": 171.84,
      |          "paymentDate": date1,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 171.84,
      |          "paymentDate": date2,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 171.84,
      |          "paymentDate": date3,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 171.84,
      |          "paymentDate": date4,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 171.84,
      |          "paymentDate": date5,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 171.84,
      |          "paymentDate": date6,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 171.84,
      |          "paymentDate": date7,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 171.84,
      |          "paymentDate": date8,
      |          "oneOffPayment": false
      |        }
      |      ],
      |      "paymentFrequency": "weekly",
      |      "previousPaymentSeq": [
      |        {
      |          "amount": 140.70,
      |          "paymentDate": previousDate1,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 140.70,
      |          "paymentDate": previousDate2,
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 100,
      |          "paymentDate": previousDate3,
      |          "oneOffPayment": true
      |        }
      |      ]
      |    },
      |    "paymentEnabled": true,
      |    "totalsByDate": [
      |      {
      |        "amount": 252.13,
      |        "paymentDate": date1
      |      },
      |      {
      |        "amount": 252.13,
      |        "paymentDate": date2
      |      },
      |      {
      |        "amount": 252.13,
      |        "paymentDate": date3
      |      },
      |      {
      |        "amount": 252.13,
      |        "paymentDate": date4
      |      },
      |      {
      |        "amount": 252.13,
      |        "paymentDate": date5
      |      },
      |      {
      |        "amount": 252.13,
      |        "paymentDate": date6
      |      },
      |      {
      |        "amount": 252.13,
      |        "paymentDate": date7
      |      },
      |      {
      |        "amount": 252.13,
      |        "paymentDate": date8
      |      }
      |    ],
      |    "previousTotalsByDate": [
      |      {
      |        "amount": 240.70,
      |        "paymentDate": previousDate1
      |      },
      |      {
      |        "amount": 240.70,
      |        "paymentDate": previousDate2
      |      },
      |      {
      |        "amount": 270.31,
      |        "paymentDate": previousDate3
      |      }
      |    ]
      |  },
      |    "personalDetails": {
      |      "forename": "Nuala",
      |      "surname": "O'Shea",
      |      "nino": "CS700100A",
      |      "address": {
      |        "addressLine1": "19 Bushey Hall Road",
      |        "addressLine2": "Bushey",
      |        "addressLine3": "Watford",
      |        "addressLine4": "Hertfordshire",
      |        "postCode": "WD23 2EE"
      |      }
      |    },
      |    "partnerDetails": {
      |      "forename": "Frederick",
      |      "otherForenames": "Tarquin",
      |      "surname": "Hunter-Smith",
      |      "nino": "CS700100A",
      |      "address": {
      |        "addressLine1": "19 Bushey Hall Road",
      |        "addressLine2": "Bushey",
      |        "addressLine3": "Watford",
      |        "addressLine4": "Hertfordshire",
      |        "postCode": "WD23 2EE"
      |      }
      |    },
      |    "children": {
      |      "child": [
      |        {
      |          "firstNames": "Sarah",
      |          "surname": "Smith",
      |          "dateOfBirth": 936057600000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        },
      |        {
      |          "firstNames": "Joseph",
      |          "surname": "Smith",
      |          "dateOfBirth": 884304000000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        },
      |        {
      |          "firstNames": "Mary",
      |          "surname": "Smith",
      |          "dateOfBirth": 852768000000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        }
      |      ]
      |    }
      |  },
      |  "state": {
      |    "enableRenewals": true
      |  },
      |  "campaigns": [
      |    {
      |      "campaignId": "HELP_TO_SAVE_1",
      |      "enabled": true,
      |      "minimumViews": 3,
      |      "dismissDays": 30,
      |      "requiredData": "workingTaxCredit"
      |    }
      |  ],
      |  "status": {
      |    "code": "complete"
      |  }
      |}
    """.stripMargin

}
