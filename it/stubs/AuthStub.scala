package stubs

import com.github.tomakehurst.wiremock.client.WireMock._

object AuthStub {

  private val oid: String = "$oid"

  def authRecordExists(nino: String, confidenceLevel: Int = 200, credentialStrength: String = "strong"): Unit = {
    stubFor(get(urlEqualTo("/auth/authority"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "uri": "/auth/oid/$oid",
             |  "confidenceLevel": $confidenceLevel,
             |  "credentialStrength": "$credentialStrength",
             |  "nino": "$nino",
             |  "userDetailsLink": "http://localhost:9978/user-details/id/59db4a285800005800576244",
             |  "legacyOid": "$oid",
             |  "new-session": "/auth/oid/$oid/session",
             |  "ids": "/auth/oid/$oid/ids",
             |  "credentials": {
             |    "gatewayId": "ghfkjlgkhl"
             |  },
             |  "accounts": {
             |    "fandf": {
             |      "nino": "$nino",
             |      "link": "/fandf/$nino"
             |    },
             |    "tai": {
             |      "nino": "$nino",
             |      "link": "/tai/$nino"
             |    },
             |    "nisp": {
             |      "nino": "$nino",
             |      "link": "/nisp/$nino"
             |    },
             |    "paye": {
             |      "nino": "$nino",
             |      "link": "/paye/$nino"
             |    },
             |    "tcs": {
             |      "nino": "$nino",
             |      "link": "/tcs/$nino"
             |    },
             |    "iht": {
             |      "nino": "$nino",
             |      "link": "/iht/$nino"
             |    }
             |  },
             |  "lastUpdated": "2017-10-09T10:06:34.190Z",
             |  "loggedInAt": "2017-10-09T10:06:34.190Z",
             |  "levelOfAssurance": "1.5",
             |  "enrolments": "/auth/oid/$oid/enrolments",
             |  "affinityGroup": "Individual",
             |  "correlationId": "8be9ca431b4b8ef3f584990d130270a84c1dbfe2d3e6c23f212d1a52f4c1f926",
             |  "credId": "cred-id"
             |}
           """.stripMargin)))
  }

  def authRecordDoesNotExist(): Unit = {
    stubFor(get(urlEqualTo("/auth/authority"))
      .willReturn(aResponse()
        .withStatus(401)))
  }

}
