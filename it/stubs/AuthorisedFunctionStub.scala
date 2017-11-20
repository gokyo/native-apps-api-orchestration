package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlEqualTo}

object AuthorisedFunctionStub {

  def authorisedFunctionSucceeds(nino : String, confidenceLevel: Int = 200, credentialStrength: String = "strong"): Unit = {
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
            |  "confidenceLevel": $confidenceLevel
            |}
          """.stripMargin)))
  }

  def authorisedFunctionGrantAccessSucceeds(nino: String, confidenceLevel: Int = 200, userDetailsUri: String = "strong") = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "nino": "$nino",
             |  "userDetailsUri": "$userDetailsUri",
             |  "confidenceLevel": $confidenceLevel
             |}
          """.stripMargin)))
  }

  def authorisedFunctionFailsWithStatus(status: Int) = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(status)))
  }

}
