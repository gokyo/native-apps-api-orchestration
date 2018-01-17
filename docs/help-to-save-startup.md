help-to-save-startup
----
    
Initiate an async service request to retrieve Help to Save portion of the full [startup](poll.md) JSON.

A cookie named mdtpapi will be returned from this service and must be
supplied to the [poll](help-to-save-startup-poll.md) service to retrieve the
response.

Use case is for the apps to be able to refresh the Help to Save info to
check if the user's Help to Save state has changed without incurring the
load involved in getting all the other startup data.
  
* **URL**

  `/native-app/:nino/startup?journeyId={id}`

* **Method:**
  
  `POST`
  
*  **Request body**

```json
{
  "serviceRequest": [
    {
      "name": "help-to-save-startup"
    }
  ]
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
  "status": {
    "code": "poll"
  }
}
```

* **Error Response:**

  * **Code:** 400 BADREQUEST <br />
    **Content:** `{"code":"BADREQUEST","message":"Bad Request"}`

  * **Code:** 404 NOTFOUND <br/>

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  * **Code:** 429 TOO MANY REQUESTS <br />
    **Content:** `{"status": { "code": "throttle"}}`

  OR for server failure

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>


