push-notification-respond-to-message
----

Introduction
============

There are two types of responses to messages:
* Acknowledge, which happens when we think that the user has seen the message, for example because it has been displayed to the user for 2 seconds (see NGC-1389).
* Answer, which happens when a user explicitly provides an answer to a message, for example by pressing a button to choose a response.

Details
=======
  
* **URL**

  `/native-app/:nino/startup?journeyId={id}`

* **Method:**
  
  `POST`
  
*  **Request body**


  To Acknowledge: 

  ```json
  {
    "serviceRequest": [
      {
        "name": "push-notification-respond-to-message",
        "data": {
          "messageId": "c59e6746-9cd8-454f-a4fd-c5dc42db7d99"
        }
      }
    ]
  }
  ```
  
  To Answer:

  ```json
  {
    "serviceRequest": [
      {
        "name": "push-notification-respond-to-message",
        "data": {
          "messageId": "c59e6746-9cd8-454f-a4fd-c5dc42db7d99",
          "answer": "yes"
        }
      }
    ]
  }
  ```

* **Success Response:**

  * **Code:** 200 OK

  * **Code:** 202 Accepted <br />
  202 is returned when a request is made to acknowledged a message that was previously acknowledged, or to answer a message that was previously answered.
  In these cases the stored response is not updated, e.g. the first answer is retained and any other answers are ignored.

* **Error Responses:**

  * **Code:** 400 Bad Request <br />
    The request was invalid, for example the JSON supplied was invalid or the messageId in the path did not match the id in the payload.

  * **Code:** 404 Not Found <br />
    No message with the specified ID was found.

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  * **Code:** 429 TOO MANY REQUESTS <br />
    **Content:** `{"status": { "code": "throttle"}}`

  OR for server failure

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>
