package uk.gov.hmrc.ngc.orchestration.services

import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.services.live.MFAIntegration
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class LiveOrchestrationServiceSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  val mockMFAIntegration = mock[MFAIntegration]
  val mockGenericConnector = mock[GenericConnector]
  val mockAuditConnector = mock[AuditConnector]
  val mockAuthConnector = mock[AuthConnector]


  "LiveOrchestrationService.preFlight" should {
    "test something" in {

      val liveOrchestrationService = new LiveOrchestrationService(mockMFAIntegration, mockGenericConnector, mockAuditConnector, mockAuthConnector, "localhost", Integer.randomInt)

    }
  }

}
