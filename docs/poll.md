poll
----
  Request for result to the Startup service. A call to Startup service must have been performed first before the poll service is invoked. The startup service will return a cookie called mdtpapi and this cookie must be supplied to the poll service. This service should be invoked every 2-3 seconds to verify the outcome of the Startup service call which executed the async task.
  
* **URL**

  `/native-app/{nino}/poll`

* **Method:**
  
  `GET`

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))


* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

If the task has not completed, the below will be returned. 
```json
{
  "status" : "poll"
}
```

On success the below JSON will be returned. Please see notes below detailing the optional attributes that can be returned. 

```
{
  "taxSummary": {
    // see https://github.com/hmrc/personal-income/blob/master/docs/tax-summary.md
  },
  "taxCreditSummary": {
    "paymentSummary": {
      "workingTaxCredit": {
        "amount": 160.45,
        "paymentDate": 1435190400000,
        "paymentFrequency": "WEEKLY"
      },
      "childTaxCredit": {
        "amount": 140.12,
        "paymentDate": 1435190400000,
        "paymentFrequency": "WEEKLY"
      }
    },
    "personalDetails": {
      "forename": "John",
      "surname": "Densmore",
      "nino": "CS700100A",
      "address": {
        "addressLine1": "13 Front Street",
        "addressLine2": "Gosforth",
        "addressLine3": "Newcastle",
        "postCode": "NE43 7AY"
      },
      "wtcPaymentFrequency": "WEEKLY",
      "ctcPaymentFrequency": "WEEKLY",
      "dayPhoneNumber": "0191 393 3993"
    },
    "children": {
      "child": [
        {
          "firstNames": "Paul",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Sasha",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Eve",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Laura",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Justine",
          "surname": "Cowling",
          "dateOfBirth": 967507200000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Adam",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": false
        },
        {
          "firstNames": "Martin",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": false
        },
        {
          "firstNames": "Sarah",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": true,
          "hasConnexions": false,
          "isActive": false
        },
        {
          "firstNames": "Jerry",
          "surname": "Cowling",
          "dateOfBirth": 1420156800000,
          "hasFTNAE": true,
          "hasConnexions": false,
          "isActive": false
        },
        {
          "firstNames": "Amy",
          "surname": "Cowling",
          "dateOfBirth": 935884800000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": false
        }
      ]
    }
  },
  "state": {
    "enableRenewals": true
  },
  "status": {
    "code": "complete"
  }
}
```

Please note the above "status" attribute could be complete, poll, error or timeout.
If the response status is "poll", the request has not completed processing. A new call is required to the `/native-app/{nino}/poll` service to understand the outcome of the call.
If the response status is "error" then a server-side failure occurred building mandatory response data.
If the response status is "timeout" then the server-side timed-out waiting for the backend services to reply.

If the response status is complete then the async service call has completed. The response will contain a set of attributes which is taxSummary, state and an optional taxCreditSummary attribute.
If the response attribute 'taxCreditSummary' is empty (contains no attributes) then this indicates Tax-Credits are available for the user, however there is no user data to display. If the response attribute 'taxCreditSummary' is not returned, then the user is not defined for tax-credits.
If the response attribute 'taxSummary' is empty (contains no attributes) then this indicates a failure retrieving the information.

| *json attribute* | *Mandatory* | *Description* |
|------------------|-------------|---------------|
| `taxSummary` | yes | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-summary.md> |
| `taxCreditSummary` | no | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-summary.md> |
| `state` | yes | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-submission-state.md> |




* **Error Response:**

  * **Code:** 400 BADREQUEST <br />
    **Content:** `{"code":"BADREQUEST","message":"Bad Request"}`

  * **Code:** 401 UNAUTHORIZED <br/>
    **Content:** `{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized for access"}`

  * **Code:** 404 NOTFOUND <br/>

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  OR when a user does not exist or server failure

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>



