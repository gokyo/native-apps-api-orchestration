package stubs

import com.github.tomakehurst.wiremock.client.WireMock._

object GenericStub {

  def versionCheckPassed() : Unit = {
    val response =
      """
        |{"upgrade":true}
      """.stripMargin
    stubPostSuccess("/profile/native-app/version-check", response)
  }

  def taxSummarySucceeds(nino : String, year: String) : Unit = {
    val response =
      s"""
        |{
        |  "taxSummaryDetails": {
        |    "nino": "$nino",
        |    "version": 1,
        |    "increasesTax": {
        |      "incomes": {
        |        "taxCodeIncomes": {
        |          "occupationalPensions": {
        |            "taxCodeIncomes": [
        |              {
        |                "name": "PAYESCHEMEOPERATORNAME52603",
        |                "taxCode": "K804",
        |                "employmentId": 1,
        |                "employmentPayeRef": "TZ99924",
        |                "employmentType": 1,
        |                "incomeType": 1,
        |                "employmentStatus": 2,
        |                "tax": {
        |                  "totalIncome": 33488,
        |                  "totalTaxableIncome": 41545,
        |                  "totalTax": 10245,
        |                  "taxBands": [
        |                    {
        |                      "income": 31865,
        |                      "tax": 6373,
        |                      "lowerBand": 0,
        |                      "upperBand": 31865,
        |                      "rate": 20
        |                    },
        |                    {
        |                      "income": 9680,
        |                      "tax": 3872,
        |                      "lowerBand": 31865,
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
        |                  "allowReliefDeducts": -8057,
        |                  "actualTaxDueAssumingBasicRateAlreadyPaid": 3547.4
        |                },
        |                "startDate": "2012-01-01",
        |                "income": 33488,
        |                "otherIncomeSourceIndicator": false,
        |                "isEditable": true,
        |                "isLive": false,
        |                "isOccupationalPension": true,
        |                "isPrimary": true
        |              }
        |            ],
        |            "totalIncome": 33488,
        |            "totalTax": 10245,
        |            "totalTaxableIncome": 41545
        |          },
        |          "hasDuplicateEmploymentNames": false,
        |          "totalIncome": 33488,
        |          "totalTaxableIncome": 41545,
        |          "totalTax": 10245
        |        },
        |        "noneTaxCodeIncomes": {
        |          "statePension": 25557,
        |          "totalIncome": 25557
        |        },
        |        "total": 59045
        |      },
        |      "benefitsFromEmployment": {
        |        "amount": 8889,
        |        "componentType": 0,
        |        "description": "",
        |        "iabdSummaries": [
        |          {
        |            "iabdType": 31,
        |            "description": "Fuel Benefit",
        |            "amount": 4444,
        |            "employmentId": 2
        |          },
        |          {
        |            "iabdType": 31,
        |            "description": "Car Benefit",
        |            "amount": 2223,
        |            "employmentId": 1,
        |            "employmentName": "PAYESCHEMEOPERATORNAME52603"
        |          },
        |          {
        |            "iabdType": 31,
        |            "description": "Car Benefit",
        |            "amount": 2222,
        |            "employmentId": 1,
        |            "employmentName": "PAYESCHEMEOPERATORNAME52603"
        |          }
        |        ]
        |      },
        |      "total": 67934
        |    },
        |    "decreasesTax": {
        |      "personalAllowance": 10000,
        |      "miscellaneous": {
        |        "amount": 15000,
        |        "componentType": 0,
        |        "description": "",
        |        "iabdSummaries": [
        |          {
        |            "iabdType": 93,
        |            "description": "Trade Union Subscriptions",
        |            "amount": 15000
        |          }
        |        ]
        |      },
        |      "paTapered": false,
        |      "total": 25000
        |    },
        |    "totalLiability": {
        |      "totalTax": 13345,
        |      "underpaymentPreviousYear": 0,
        |      "outstandingDebt": 0,
        |      "childBenefitTaxDue": 100,
        |      "liabilityReductions": {
        |        "enterpriseInvestmentSchemeRelief": {
        |          "codingAmount": 0,
        |          "amountInTermsOfTax": 0
        |        },
        |        "concessionalRelief": {
        |          "codingAmount": 0,
        |          "amountInTermsOfTax": 0
        |        },
        |        "maintenancePayments": {
        |          "codingAmount": 0,
        |          "amountInTermsOfTax": 0
        |        },
        |        "doubleTaxationRelief": {
        |          "codingAmount": 0,
        |          "amountInTermsOfTax": 0
        |        }
        |      },
        |      "liabilityAdditions": {
        |        "excessGiftAidTax": {
        |          "codingAmount": 0,
        |          "amountInTermsOfTax": 0
        |        },
        |        "excessWidowsAndOrphans": {
        |          "codingAmount": 0,
        |          "amountInTermsOfTax": 0
        |        },
        |        "pensionPaymentsAdjustment": {
        |          "codingAmount": 0,
        |          "amountInTermsOfTax": 0
        |        }
        |      }
        |    },
        |    "extensionReliefs": {
        |      "giftAid": {
        |        "sourceAmount": 0,
        |        "reliefAmount": 0
        |      },
        |      "personalPension": {
        |        "sourceAmount": 0,
        |        "reliefAmount": 0
        |      }
        |    },
        |    "taxCodeDetails": {
        |      "employment": [
        |        {
        |          "id": 1,
        |          "name": "PAYESCHEMEOPERATORNAME52603",
        |          "taxCode": "K804"
        |        }
        |      ],
        |      "taxCode": [
        |        {
        |          "taxCode": "K"
        |        }
        |      ],
        |      "taxCodeDescriptions": [
        |        {
        |          "taxCode": "K804",
        |          "name": "PAYESCHEMEOPERATORNAME52603",
        |          "taxCodeDescriptors": [
        |            {
        |              "taxCode": "K"
        |            }
        |          ]
        |        }
        |      ],
        |      "deductions": [
        |        {
        |          "description": "savings income taxable at higher rate",
        |          "amount": 7500,
        |          "componentType": 32
        |        },
        |        {
        |          "description": "state pension/state benefits",
        |          "amount": 25557,
        |          "componentType": 1
        |        }
        |      ],
        |      "allowances": [
        |        {
        |          "description": "Tax Free Amount",
        |          "amount": 25000,
        |          "componentType": 0
        |        }
        |      ],
        |      "splitAllowances": false,
        |      "total": -8057
        |    }
        |  },
        |  "baseViewModel": {
        |    "estimatedIncomeTax": 13345,
        |    "taxableIncome": 67934,
        |    "taxFree": 25000,
        |    "personalAllowance": 10000,
        |    "hasTamc": false,
        |    "taxCodesList": [
        |      "K804"
        |    ],
        |    "hasChanges": false,
        |    "simpleTaxUser": false
        |  },
        |  "estimatedIncomeWrapper": {
        |    "estimatedIncome": {
        |      "increasesTax": true,
        |      "incomeTaxEstimate": 13345,
        |      "incomeEstimate": 67934,
        |      "taxFreeEstimate": 25000,
        |      "taxRelief": false,
        |      "taxCodes": [
        |        "K804"
        |      ],
        |      "potentialUnderpayment": false,
        |      "additionalTaxTable": [
        |        {
        |          "a": "Child Benefit",
        |          "b": "100.00"
        |        }
        |      ],
        |      "additionalTaxTableTotal": "100.00",
        |      "reductionsTable": [
        |
        |      ],
        |      "reductionsTableTotal": "-0.00",
        |      "graph": {
        |        "id": "taxGraph",
        |        "bands": [
        |          {
        |            "colour": "",
        |            "barPercentage": 0,
        |            "tablePercentage": "0",
        |            "income": 25000,
        |            "tax": 0,
        |            "bandType": "pa"
        |          },
        |          {
        |            "colour": "",
        |            "barPercentage": 0,
        |            "tablePercentage": "20",
        |            "income": 31865,
        |            "tax": 6373,
        |            "bandType": "NA"
        |          },
        |          {
        |            "colour": "",
        |            "barPercentage": 0,
        |            "tablePercentage": "40",
        |            "income": 2180,
        |            "tax": 872,
        |            "bandType": "NA"
        |          },
        |          {
        |            "colour": "",
        |            "barPercentage": 0,
        |            "tablePercentage": "40",
        |            "income": 15000,
        |            "tax": 6000,
        |            "bandType": "NA"
        |          }
        |        ],
        |        "minBand": 0,
        |        "nextBand": 0,
        |        "incomeTotal": 74045,
        |        "zeroIncomeAsPercentage": 0,
        |        "zeroIncomeTotal": 0,
        |        "incomeAsPercentage": 0,
        |        "taxTotal": 13245
        |      },
        |      "hasChanges": false,
        |      "nextYearTaxTotal": 0,
        |      "hasPSA": false,
        |      "hasSSR": false
        |    }
        |  },
        |  "taxableIncome": {
        |    "taxFreeAmount": 25000,
        |    "incomeTax": 13345,
        |    "income": 67934,
        |    "taxCodeList": [
        |      "K804"
        |    ],
        |    "increasesTax": {
        |      "incomes": {
        |        "taxCodeIncomes": {
        |          "occupationalPensions": {
        |            "taxCodeIncomes": [
        |              {
        |                "name": "PAYESCHEMEOPERATORNAME52603",
        |                "taxCode": "K804",
        |                "employmentId": 1,
        |                "employmentPayeRef": "TZ99924",
        |                "employmentType": 1,
        |                "incomeType": 1,
        |                "employmentStatus": 2,
        |                "tax": {
        |                  "totalIncome": 33488,
        |                  "totalTaxableIncome": 41545,
        |                  "totalTax": 10245,
        |                  "taxBands": [
        |                    {
        |                      "income": 31865,
        |                      "tax": 6373,
        |                      "lowerBand": 0,
        |                      "upperBand": 31865,
        |                      "rate": 20
        |                    },
        |                    {
        |                      "income": 9680,
        |                      "tax": 3872,
        |                      "lowerBand": 31865,
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
        |                  "allowReliefDeducts": -8057,
        |                  "actualTaxDueAssumingBasicRateAlreadyPaid": 3547.4
        |                },
        |                "startDate": "2012-01-01",
        |                "income": 33488,
        |                "otherIncomeSourceIndicator": false,
        |                "isEditable": true,
        |                "isLive": false,
        |                "isOccupationalPension": true,
        |                "isPrimary": true
        |              }
        |            ],
        |            "totalIncome": 33488,
        |            "totalTax": 10245,
        |            "totalTaxableIncome": 41545
        |          },
        |          "hasDuplicateEmploymentNames": false,
        |          "totalIncome": 33488,
        |          "totalTaxableIncome": 41545,
        |          "totalTax": 10245
        |        },
        |        "noneTaxCodeIncomes": {
        |          "statePension": 25557,
        |          "totalIncome": 25557
        |        },
        |        "total": 59045
        |      },
        |      "benefitsFromEmployment": {
        |        "amount": 8889,
        |        "componentType": 0,
        |        "description": "",
        |        "iabdSummaries": [
        |          {
        |            "iabdType": 31,
        |            "description": "Fuel Benefit",
        |            "amount": 4444,
        |            "employmentId": 2
        |          },
        |          {
        |            "iabdType": 31,
        |            "description": "Car Benefit",
        |            "amount": 2223,
        |            "employmentId": 1,
        |            "employmentName": "PAYESCHEMEOPERATORNAME52603"
        |          },
        |          {
        |            "iabdType": 31,
        |            "description": "Car Benefit",
        |            "amount": 2222,
        |            "employmentId": 1,
        |            "employmentName": "PAYESCHEMEOPERATORNAME52603"
        |          }
        |        ]
        |      },
        |      "total": 67934
        |    },
        |    "employmentPension": {
        |      "taxCodeIncomes": {
        |        "occupationalPensions": {
        |          "taxCodeIncomes": [
        |            {
        |              "name": "PAYESCHEMEOPERATORNAME52603",
        |              "taxCode": "K804",
        |              "employmentId": 1,
        |              "employmentPayeRef": "TZ99924",
        |              "employmentType": 1,
        |              "incomeType": 1,
        |              "employmentStatus": 2,
        |              "tax": {
        |                "totalIncome": 33488,
        |                "totalTaxableIncome": 41545,
        |                "totalTax": 10245,
        |                "taxBands": [
        |                  {
        |                    "income": 31865,
        |                    "tax": 6373,
        |                    "lowerBand": 0,
        |                    "upperBand": 31865,
        |                    "rate": 20
        |                  },
        |                  {
        |                    "income": 9680,
        |                    "tax": 3872,
        |                    "lowerBand": 31865,
        |                    "upperBand": 150000,
        |                    "rate": 40
        |                  },
        |                  {
        |                    "income": 0,
        |                    "tax": 0,
        |                    "lowerBand": 150000,
        |                    "upperBand": 0,
        |                    "rate": 45
        |                  }
        |                ],
        |                "allowReliefDeducts": -8057,
        |                "actualTaxDueAssumingBasicRateAlreadyPaid": 3547.4
        |              },
        |              "startDate": "2012-01-01",
        |              "income": 33488,
        |              "otherIncomeSourceIndicator": false,
        |              "isEditable": true,
        |              "isLive": false,
        |              "isOccupationalPension": true,
        |              "isPrimary": true
        |            }
        |          ],
        |          "totalIncome": 33488,
        |          "totalTax": 10245,
        |          "totalTaxableIncome": 41545
        |        },
        |        "hasDuplicateEmploymentNames": false,
        |        "totalIncome": 33488,
        |        "totalTaxableIncome": 41545,
        |        "totalTax": 10245
        |      },
        |      "totalEmploymentPensionAmt": 33488,
        |      "hasEmployment": false,
        |      "isOccupationalPension": true
        |    },
        |    "investmentIncomeData": [
        |
        |    ],
        |    "investmentIncomeTotal": 0,
        |    "otherIncomeData": [
        |
        |    ],
        |    "otherIncomeTotal": 0,
        |    "benefitsData": [
        |      {
        |        "a": "Car benefit for ",
        |        "b": "4,444",
        |        "c": "The value of the benefit on the car(s) provided by your employer(s).",
        |        "d": "",
        |        "e": 2,
        |        "f": 31
        |      },
        |      {
        |        "a": "Car benefit for PAYESCHEMEOPERATORNAME52603",
        |        "b": "2,223",
        |        "c": "The value of the benefit on the car(s) provided by your employer(s).",
        |        "d": "",
        |        "e": 1,
        |        "f": 31
        |      },
        |      {
        |        "a": "Car benefit for PAYESCHEMEOPERATORNAME52603",
        |        "b": "2,222",
        |        "c": "The value of the benefit on the car(s) provided by your employer(s).",
        |        "d": "",
        |        "e": 1,
        |        "f": 31
        |      }
        |    ],
        |    "benefitsTotal": 8889,
        |    "taxableBenefitsData": [
        |      {
        |        "a": "State Pension or other state benefits",
        |        "b": "25,557",
        |        "c": "The amount of State Pension or other State Benefits you receive."
        |      }
        |    ],
        |    "taxableBenefitsTotal": 25557,
        |    "hasChanges": false
        |  }
        |}
      """.stripMargin
    stubGetSuccess(s"/income/$nino/tax-summary/$year", response)
  }

  def taxCreditSummarySucceeds(nino: String) : Unit = {
    val response =
      s"""
        |{
        |  "paymentSummary": {
        |    "workingTaxCredit": {
        |      "paymentSeq": [
        |        {
        |          "amount": 160.45,
        |          "paymentDate": 1508367600000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 160.45,
        |          "paymentDate": 1508972400000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 160.45,
        |          "paymentDate": 1509580800000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 160.45,
        |          "paymentDate": 1510185600000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 160.45,
        |          "paymentDate": 1510790400000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 160.45,
        |          "paymentDate": 1511395200000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 160.45,
        |          "paymentDate": 1512000000000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 160.45,
        |          "paymentDate": 1512604800000,
        |          "oneOffPayment": false
        |        }
        |      ],
        |      "paymentFrequency": "WEEKLY"
        |    },
        |    "childTaxCredit": {
        |      "paymentSeq": [
        |        {
        |          "amount": 140.12,
        |          "paymentDate": 1508367600000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 140.12,
        |          "paymentDate": 1508972400000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 140.12,
        |          "paymentDate": 1509580800000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 140.12,
        |          "paymentDate": 1510185600000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 140.12,
        |          "paymentDate": 1510790400000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 140.12,
        |          "paymentDate": 1511395200000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 140.12,
        |          "paymentDate": 1512000000000,
        |          "oneOffPayment": false
        |        },
        |        {
        |          "amount": 140.12,
        |          "paymentDate": 1512604800000,
        |          "oneOffPayment": false
        |        }
        |      ],
        |      "paymentFrequency": "WEEKLY"
        |    },
        |    "paymentEnabled": true,
        |    "totalsByDate": [
        |      {
        |        "amount": 300.57,
        |        "paymentDate": 1508367600000
        |      },
        |      {
        |        "amount": 300.57,
        |        "paymentDate": 1508972400000
        |      },
        |      {
        |        "amount": 300.57,
        |        "paymentDate": 1509580800000
        |      },
        |      {
        |        "amount": 300.57,
        |        "paymentDate": 1510185600000
        |      },
        |      {
        |        "amount": 300.57,
        |        "paymentDate": 1510790400000
        |      },
        |      {
        |        "amount": 300.57,
        |        "paymentDate": 1511395200000
        |      },
        |      {
        |        "amount": 300.57,
        |        "paymentDate": 1512000000000
        |      },
        |      {
        |        "amount": 300.57,
        |        "paymentDate": 1512604800000
        |      }
        |    ]
        |  },
        |  "personalDetails": {
        |    "forename": "John",
        |    "surname": "Densmore",
        |    "nino": "$nino",
        |    "address": {
        |      "addressLine1": "13 Front Street",
        |      "addressLine2": "Gosforth",
        |      "addressLine3": "Newcastle",
        |      "postCode": "NE43 7AY"
        |    },
        |    "wtcPaymentFrequency": "WEEKLY",
        |    "ctcPaymentFrequency": "WEEKLY",
        |    "dayPhoneNumber": "0191 393 3993"
        |  },
        |  "children": {
        |    "child": [
        |      {
        |        "firstNames": "Paul",
        |        "surname": "Cowling",
        |        "dateOfBirth": 1451606400000,
        |        "hasFTNAE": false,
        |        "hasConnexions": false,
        |        "isActive": true
        |      },
        |      {
        |        "firstNames": "Sasha",
        |        "surname": "Cowling",
        |        "dateOfBirth": 1451606400000,
        |        "hasFTNAE": false,
        |        "hasConnexions": false,
        |        "isActive": true
        |      },
        |      {
        |        "firstNames": "Eve",
        |        "surname": "Cowling",
        |        "dateOfBirth": 1451606400000,
        |        "hasFTNAE": false,
        |        "hasConnexions": false,
        |        "isActive": true
        |      },
        |      {
        |        "firstNames": "Claire",
        |        "surname": "Cowling",
        |        "dateOfBirth": 962319600000,
        |        "hasFTNAE": true,
        |        "hasConnexions": false,
        |        "isActive": true
        |      },
        |      {
        |        "firstNames": "Micheal",
        |        "surname": "Cowling",
        |        "dateOfBirth": 930697200000,
        |        "hasFTNAE": true,
        |        "hasConnexions": false,
        |        "isActive": true
        |      },
        |      {
        |        "firstNames": "Justine",
        |        "surname": "Cowling",
        |        "dateOfBirth": 1025391600000,
        |        "hasFTNAE": false,
        |        "hasConnexions": false,
        |        "isActive": true
        |      },
        |      {
        |        "firstNames": "Mathias",
        |        "surname": "Cowling",
        |        "dateOfBirth": 962319600000,
        |        "hasFTNAE": true,
        |        "hasConnexions": true,
        |        "isActive": true
        |      }
        |    ]
        |  }
        |}
      """.stripMargin
    stubGetSuccess(s"/income/$nino/tax-credits/tax-credits-summary", response)
  }

  def taxCreditsDecisionSucceeds(nino: String) : Unit = {
    val response =
      """
        |{"showData":true}
      """.stripMargin
    stubGetSuccess(s"/income/$nino/tax-credits/tax-credits-decision", response)
  }

  def taxCreditsSubmissionStateIsEnabled() : Unit = {
    val response =
      """
        |{"submissionState":false}
      """.stripMargin
    stubGetSuccess("/income/tax-credits/submission/state/enabled", response)
  }

  def pushRegistrationSucceeds() : Unit = {
    stubPostSuccess("/push/registration", """{}""")
  }

  private def stubGetSuccess(path: String, response: String) : Unit = {
    stubFor(get(urlEqualTo(path))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(response)))
  }

  private def stubPostSuccess(path: String, response: String) : Unit = {
    stubFor(post(urlEqualTo(path))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(response)))
  }

}
