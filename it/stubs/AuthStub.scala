package stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.{JsObject, JsString, Json}

/**
  * Design notes
  *
  * The public API of this class is designed to operate in terms of the
  * characteristics of the calling user, not the details of what auth will
  * returned.
  * 
  * The /auth/authorise endpoint is very flexible so we only stub particular
  * calls to it that we know this service (native-apps-api-orchestration)
  * makes. That means that when we change this service to call
  * /auth/authorise in different ways then we will need to extend or alter
  * this stubbing.
  */
object AuthStub {

  def authorisedWithStrongCredentials(nino: String): Unit = {
    authoriseWithNoPredicatesWillReturn200(Some(nino), credentialStrengthToReturn = "strong")
    authoriseWithNinoPredicateWillReturn200ForMatchingNino(nino, predicateCredentialStrength = "strong")
  }

  def authorisedWithNinoOnlyReturningWeakCrednetialStrength(nino: String): Unit = {
    authoriseWithNoPredicatesWillReturn200(Some(nino), credentialStrengthToReturn = "weak")
    authoriseWithNinoOnlyMatchingNino(nino)
  }

  def authorisedWithWeakCredentials(nino: String): Unit = {
    authoriseWithNoPredicatesWillReturn200(Some(nino), credentialStrengthToReturn = "weak")
    authoriseWithNinoPredicateWillReturn200ForMatchingNino(nino, predicateCredentialStrength = "weak")
  }

  def authorisedWithStrongCredentialsAndNoNino(): Unit = {
    authoriseWithNoPredicatesWillReturn200(nino = None, credentialStrengthToReturn = "weak")
    authorisedWithNinoPredicateWillReturn401()
  }

  def authorisedWithStrongCredentialsAndNotGovernmentGateway(nino: String): Unit = {
    authoriseWithNoPredicatesWillReturn200(nino = None, credentialStrengthToReturn = "weak", "Not_Government_Gateway")
    authoriseWithNinoPredicateWillReturn200ForMatchingNino(nino, predicateCredentialStrength = "strong")
  }

  def notAuthorised(): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(401)))
  }

  private def authoriseWithNoPredicatesWillReturn200(nino: Option[String], credentialStrengthToReturn: String, providerType: String = "GovernmentGateway"): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
        .withRequestBody(equalToJson(
          """
            |{
            |  "authorise": [],
            |  "retrieve": [
            |    "nino",
            |    "saUtr",
            |    "affinityGroup",
            |    "credentials",
            |    "credentialStrength",
            |    "confidenceLevel"
            |  ]
            |}
          """.stripMargin,
          true,
          false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(addNinoIfDefined(
          Json.obj(
            "affinityGroup" → "Individual",
            "credentials" → Json.obj(
              "providerId" → "Some-Cred-Id",
              "providerType" → providerType
            ),
            "credentialStrength" -> credentialStrengthToReturn,
            "confidenceLevel" -> 200
          ),
          nino).toString)))
  }

  private def addNinoIfDefined(input: JsObject, maybeNino: Option[String]): JsObject = {
    maybeNino.fold(input)(nino => input + ("nino" -> JsString(nino)))
  }

  private def authoriseWithNinoPredicateWillReturn200ForMatchingNino(nino: String, predicateCredentialStrength: String): Unit = {
    // catch-all case for when /auth/authorised is called with a non-matching NINO
    authorisedWithNinoPredicateWillReturn401(1)

    // specific case for when /auth/authorise is called with a matching NINO
    stubFor(post(urlEqualTo("/auth/authorise"))
        .atPriority(0)
        .withRequestBody(equalToJson(
          s"""
            |{
            |  "authorise": [
            |    {
            |      "enrolment": "HMRC-NI",
            |      "identifiers": [
            |        {
            |          "key": "NINO",
            |          "value": "$nino"
            |        }
            |      ],
            |      "state": "Activated"
            |    },
            |    {
            |      "credentialStrength": "$predicateCredentialStrength"
            |    }
            |  ],
            |  "retrieve": [
            |    "nino",
            |    "confidenceLevel"
            |  ]
            |}
          """.stripMargin,
          true,
          false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "nino": "$nino",
             |  "userDetailsUri": "/test-user-details",
             |  "confidenceLevel": 200
             |}
          """.stripMargin)))
  }

  private def authoriseWithNinoOnlyMatchingNino(nino: String): Unit = {
    // catch-all case for when /auth/authorised is called with a non-matching NINO
    authorisedWithNinoPredicateWillReturn401(1)

    // specific case for when /auth/authorise is called with a matching NINO
    stubFor(post(urlEqualTo("/auth/authorise"))
        .atPriority(0)
        .withRequestBody(equalToJson(
          s"""
            |{
            |  "authorise": [
            |    {
            |      "enrolment": "HMRC-NI",
            |      "identifiers": [
            |        {
            |          "key": "NINO",
            |          "value": "$nino"
            |        }
            |      ],
            |      "state": "Activated"
            |    }
            |  ],
            |  "retrieve": [
            |    "nino",
            |    "confidenceLevel"
            |  ]
            |}
          """.stripMargin,
          true,
          false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "nino": "$nino",
             |  "userDetailsUri": "/test-user-details",
             |  "confidenceLevel": 200
             |}
          """.stripMargin)))
  }

  private def authorisedWithNinoPredicateWillReturn401(priority: Int = StubMapping.DEFAULT_PRIORITY): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .atPriority(priority)
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "authorise": [
           |    {
           |      "enrolment": "HMRC-NI",
           |      "identifiers": [
           |        {
           |          "key": "NINO"
           |        }
           |      ],
           |      "state": "Activated"
           |    }
           |  ]
           |}
        """.stripMargin,
        true,
        true))
      .willReturn(aResponse()
          .withStatus(401)))
  }

}
