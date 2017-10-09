package stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import utils.WireMockSupport

trait GenericStub {

  self: WireMockSupport ⇒

  def versionCheckPassed: GenericStub = {
    stubFor(get(urlEqualTo("/profile/native-app/version-check"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{"upgrade":true}
           """.stripMargin)))
      this
  }

}
