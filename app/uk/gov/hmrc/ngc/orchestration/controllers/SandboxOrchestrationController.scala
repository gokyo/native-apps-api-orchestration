/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.ngc.orchestration.controllers

import javax.inject.{Named, Singleton}

import akka.actor.ActorSystem
import com.google.inject.Inject
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, BodyParsers, Cookie}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.msasync.repository.AsyncRepository
import uk.gov.hmrc.ngc.orchestration.services.{PreFlightRequest, SandboxOrchestrationService}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait SandboxOrchestrationController extends NativeAppsOrchestrationController with SandboxPoll {
  override val actorName = "sandbox-async_native-apps-api-actor"
  override def id = "sandbox-async_native-apps-api-id"
  override val service: SandboxOrchestrationService
  override val app: String = "Sandbox-Orchestration-Controller"
  override lazy val repository:AsyncRepository = sandboxRepository

  override def preFlightCheck(journeyId:Option[String]): Action[JsValue] = validateAccept(acceptHeaderValidationRules).async(BodyParsers.parse.json) {
    implicit request =>
      errorWrapper {
        implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
          .withExtraHeaders("X-MOBILE-USER-ID" -> request.headers.get("X-MOBILE-USER-ID").getOrElse("404893573708"))
        implicit val context: ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails

        Json.toJson(request.body).asOpt[PreFlightRequest].
          fold(Future.successful(BadRequest("Failed to parse request!"))) { preFlightRequest =>
            hc.authorization match {
              case Some(auth) => service.preFlightCheck(preFlightRequest, journeyId).map(
                response => Ok(Json.toJson(response)).withSession(authToken -> auth.value)
              )

              case _ => Future.failed(new Exception("Failed to resolve authentication from HC!"))
            }
          }
      }
  }

  // Must override the startup call since live controller talks to a queue.
  override def orchestrate(nino: Nino, journeyId: Option[String] = None): Action[AnyContent] = validateAccept(acceptHeaderValidationRules).async {
    implicit request ⇒
      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
      errorWrapper {
        validate { validatedRequest ⇒
          service.orchestrate(validatedRequest, nino, journeyId).map(resp ⇒


            Ok(resp).withCookies(Cookie("mdtpapi", buildRequestsCookie(journeyId))))
        }
      }
  }

  // Override the poll and return static resource.
  override def poll(nino: Nino, journeyId: Option[String] = None) = validateAccept(acceptHeaderValidationRules).async {
    implicit authenticated =>
      errorWrapper {
        authenticated.cookies.get("mdtpapi").get.value
        Future.successful(addCacheHeader(maxAgeForSuccess, Ok(pollSandboxResult(nino, authenticated.cookies.get("mdtpapi")).value)))
      }
  }

  def buildRequestsCookie(journeyId: Option[String]): String = {
    service.getGenericExecutions(journeyId).getOrElse("")
  }
}

@Singleton
class SandboxOrchestrationControllerImpl @Inject()(
  override val appNameConfiguration: Configuration,
  override val auditConnector: AuditConnector,
  override val authConnector: AuthConnector,
  override val service: SandboxOrchestrationService,
  override val actorSystem: ActorSystem,
  override val lifecycle: ApplicationLifecycle,
  @Named("supported.generic.service.max") override val serviceMax: Int,
  @Named("supported.generic.event.max") override val eventMax: Int,
  @Named("controllers.confidenceLevel") override val confLevel: Int) extends SandboxOrchestrationController {
  override val maxAgeForSuccess: Int = 14400
}
