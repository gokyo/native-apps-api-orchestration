package utils

import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec

trait BaseISpec extends UnitSpec with OneServerPerSuite with WireMockSupport {
  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.service-locator.enabled" → false,
        "auditing.enabled"                              → false,
        "microservice.services.auth.host"               → wireMockHost,
        "microservice.services.auth.port"               → wireMockPort,
        "microservice.services.service-locator.host"    → wireMockHost,
        "microservice.services.service-locator.port"    → wireMockPort,
        "microservice.services.customer-profile.host"   → wireMockHost,
        "microservice.services.customer-profile.port"   → wireMockPort,
        "microservice.services.personal-income.host"    → wireMockHost,
        "microservice.services.personal-income.port"    → wireMockPort,
        "microservice.services.push-registration.host"  → wireMockHost,
        "microservice.services.push-registration.port"  → wireMockPort,
        "auditing.consumer.baseUri.host"                → wireMockHost,
        "auditing.consumer.baseUri.port"                → wireMockPort
      )
}
