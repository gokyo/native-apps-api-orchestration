import sbt._

object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._

  private val microserviceBootstrapVersion = "6.10.0"
  private val domainVersion = "5.0.0"
  private val playHmrcApiVersion = "2.1.0"
  private val hmrcEmailAddressVersion = "2.1.0"
  private val microserviceAsyncVersion = "2.0.0"
  private val reactiveMongoVersion = "5.2.0"
  private val taxYearVersion = "0.3.0"

  private val hmrcTestVersion = "2.4.0"
  private val mockitoVersion = "2.11.0"
  private val pegdownVersion = "1.6.0"
  private val scalaTestVersion = "3.0.4"
  private val wireMockVersion = "2.9.0"
  private val scalaTestPlusVersion = "2.0.1"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "microservice-async" % microserviceAsyncVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "play-hmrc-api" % playHmrcApiVersion,
    "uk.gov.hmrc" %% "emailaddress" % hmrcEmailAddressVersion,
    "uk.gov.hmrc" %% "play-reactivemongo" % reactiveMongoVersion,
    "uk.gov.hmrc" %% "tax-year" % taxYearVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % scalaTestVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.mockito" % "mockito-core" % mockitoVersion % scope
      )
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "com.github.tomakehurst" % "wiremock" % wireMockVersion % scope,
        "org.mockito" % "mockito-core" % mockitoVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusVersion % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}
