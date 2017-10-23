package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlEqualTo}

object AuthorisedFunctionStub {

  def authorisedFunctionSucceeds(nino : String, confidenceLevel: Int, credentialStrength: String): Unit = {
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

  def authorisedFunctionGrantAccessSucceeds(nino: String, confidenceLevel: Int, userDetailsUri: String) = {
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

}
