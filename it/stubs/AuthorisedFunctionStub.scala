package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlEqualTo}

object AuthorisedFunctionStub {

  def authorisedFunctionSucceeds(nino: String, credentialStrength: String = "strong"): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
            |{
            |  "nino": "$nino",
            |  "affinityGroup": "Individual",
            |  "authProviderId": {
            |    "ggCredId": "Some-Cred-Id"
            |  },
            |  "credentialStrength": "$credentialStrength",
            |  "confidenceLevel": 200
            |}
          """.stripMargin)))
  }

  def authorisedFunctionGrantAccessSucceeds(nino: String): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
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

  def authoriseWillReturnNotUnauthorised(): Unit = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(401)))
  }

}
