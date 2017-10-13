package stubs

import com.github.tomakehurst.wiremock.client.WireMock._

object GenericStub {

  def versionCheckPassed() : Unit = {
    stubFor(post(urlPathEqualTo(s"/profile/native-app/version-check"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{"upgrade":true}
           """.stripMargin)))
  }

}
