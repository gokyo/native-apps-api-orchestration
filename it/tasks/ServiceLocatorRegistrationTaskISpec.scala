package tasks

import com.github.tomakehurst.wiremock.client.WireMock.{equalTo, postRequestedFor, urlMatching, verify}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.test.PlayRunners
import stubs.ServiceLocatorStub._
import uk.gov.hmrc.ngc.orchestration.tasks.ServiceLocatorRegistrationTask
import utils.{BaseISpec, WiremockServiceLocatorSugar}

class ServiceLocatorRegistrationTaskISpec extends BaseISpec with Eventually with WiremockServiceLocatorSugar with ScalaFutures with PlayRunners{
  "ServiceLocatorRegistrationTask" should {
    val task = app.injector.instanceOf[ServiceLocatorRegistrationTask]

    "register with the api platform" in {
      registrationWillSucceed()
      await(task.register) shouldBe true
      verify(1,
        postRequestedFor(urlMatching("/registration")).withHeader("content-type", equalTo("application/json")).
          withRequestBody(equalTo(regPayloadStringFor("native-apps-api-orchestration", "http://native-apps-api-orchestration.protected.mdtp")))
      )
    }

    "handle errors" in {
      registrationWillFail()
      await(task.register) shouldBe false
    }
  }
}
