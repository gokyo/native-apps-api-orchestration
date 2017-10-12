package stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlEqualTo}

object DataStreamStub {

  def writeAuditSucceeds(): Unit = {
    stubFor(post(urlEqualTo(auditUrl))
      .willReturn(aResponse()
        .withStatus(200)
      ))
  }

  private def auditUrl = "/write/audit"
}
