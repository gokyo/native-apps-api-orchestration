/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Named, Provider, Singleton}

import akka.actor.ActorSystem
import play.api.http.HeaderNames
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsSuccess, JsValue, Json}
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.api.controllers.HeaderValidator
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.msasync.repository.{AsyncMongoRepository, AsyncRepository}
import uk.gov.hmrc.ngc.orchestration.services.{Result => _, _}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.asyncmvc.model.AsyncMvcSession
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


trait NativeAppsOrchestrationController extends AsyncController with Auditor with GenericServiceCheck with HeaderValidator with Authorisation with ErrorHandling {
  val service: OrchestrationService
  val serviceMax: Int
  val eventMax: Int
  val maxAgeForSuccess: Int
  val parsingFailure: Future[Result] = Future.successful(BadRequest("Failed to parse request!"))
  val authenticationFailure: Future[Nothing] = Future.failed(new Exception("Failed to resolve authentication from HC!"))

  def preFlightCheck(journeyId:Option[String]): Action[JsValue] = validateAccept(acceptHeaderValidationRules).async(BodyParsers.parse.json) {
    implicit request ⇒ {
      errorWrapper {
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
        implicit val context: ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails
        request.body.validate[PreFlightRequest] match {
          case JsSuccess(preFlightRequest, _) ⇒
            hc.authorization match {
              case Some(auth) ⇒ service.preFlightCheck(preFlightRequest, journeyId).map { response ⇒
                Ok(Json.toJson(response)).withSession(authToken -> auth.value)
              }
              case _ ⇒ authenticationFailure
            }
          case _ ⇒ parsingFailure
        }
      }
    }
  }

  def orchestrate(reqNino: Nino, journeyId: Option[String] = None): Action[AnyContent] = validateAccept(acceptHeaderValidationRules).async {
    implicit request ⇒ {
      errorWrapper {
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
        implicit val context: ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails
        validate { valid ⇒
          // Do not allow more than one task to be executing - if task is running then poll status will be returned.
          asyncActionWrapper.async(callbackWithStatus) { _ ⇒
              // Async function wrapper responsible for executing below code onto a background queue.
            asyncWrapper(callbackWithStatus) { _ ⇒
              Logger.info(s"Background HC: ${hc.authorization.fold("not found")(_.value)} for Journey Id $journeyId")
              service.orchestrate(valid, reqNino, journeyId).map { response ⇒
                AsyncResponse( response ++ buildResponseCode(ResponseStatus.complete), reqNino)
              }.recover{
                case e: GrantAccessException =>
                  Logger.info(e.getMessage)
                  AsyncResponse(buildResponseCode(ResponseStatus.error), reqNino)
              }
            }
          }
        }
      }
    }
  }

  /**
   * Invoke the library poll function to determine the response to the client.
   */
  def poll(nino: Nino, journeyId: Option[String] = None): Action[AnyContent] = validateAccept(acceptHeaderValidationRules).async {
    implicit request ⇒
      withAudit("poll", Map("nino" → nino.value)) {
        errorWrapper {
          implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
          implicit val context: ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails
          grantAccess(nino).map { auth ⇒
            implicit val authority: Option[Authority] = Some(auth)
            implicit val requestNino: Nino = nino
            val session: Option[AsyncMvcSession] = getSessionObject

            def withASyncSession(data: Map[String, String]): Map[String, String] = {
              data - AsyncMVCSessionId + (AsyncMVCSessionId → Json.stringify(Json.toJson(session)))
            }

            // Make a request to understand the status of the async task.
            // Please note the async library will update the session and remove the task id from session once the task completes.
            val response = pollTask(Call("GET", "/notaskrunning"), callbackWithSuccessResponse, callbackWithStatus)

            // Convert 303 response to 404.
            // The 303 is generated (with URL "notaskrunning") when no task Id exists in the users session!
            response.map { resp ⇒
              resp.header.status match {
                case 303 ⇒
                  val now = DateTimeUtils.now.getMillis
                  session match {
                    case Some(s) ⇒
                      Logger.info(s"Native - Poll Task not in cache! Client start request time ${s.start - getClientTimeout} - Client timeout ${s.start} - Current time $now. Journey Id $journeyId")

                    case None ⇒
                      Logger.info(s"Native - Poll - no session object found! Journey Id $journeyId")
                  }
                  NotFound
                case _ ⇒
                  // Add the task Id back into session. This allows the user to re-call the poll service once complete.
                  resp.withSession(resp.session.copy(data = withASyncSession(resp.session.data)))
              }
            }
          }.flatMap(result ⇒ result)
        }
      }
  }

  def addCacheHeader(maxAge:Long, result:Result):Result = {
    result.withHeaders(HeaderNames.CACHE_CONTROL → s"max-age=$maxAge")
  }

  /**
   *  Callback from async framework to generate the successful Result. The off-line has task completed successfully.
   */
  val nino_compare_length = 8
  def callbackWithSuccessResponse(response:AsyncResponse)(id:String)(implicit request:Request[AnyContent], authority:Option[Authority], requestNino:Nino) : Future[Result] = {
    def noAuthority = throw new Exception("Failed to resolve authority")
    def success = addCacheHeader(maxAgeForSuccess, Ok(response.value))

    val responseNinoTaxSummary = (response.value \ "taxSummary" \ "taxSummaryDetails" \ "nino").asOpt[String]
    val responseNinoCreditSummary = (response.value \ "taxCreditSummary" \ "personalDetails" \ "nino").asOpt[String]
    val ninoCheck = (responseNinoTaxSummary, responseNinoCreditSummary) match {
      case (None, None) => Some(response.nino.value)
      case (taxSummaryNino, None) => taxSummaryNino
      case (None, taxCreditSummaryNino) => taxCreditSummaryNino
      case (taxSummaryNino, taxCreditSummaryNino) => taxSummaryNino
    }

    val result = if (ninoCheck.isDefined) {
      val nino = ninoCheck.getOrElse("No NINO found in response!").take(nino_compare_length)
      val authNino = authority.getOrElse(noAuthority).nino.value.take(nino_compare_length)

      // Check request nino matches the authority record.
      if (!requestNino.value.take(nino_compare_length).equals(authNino)) {
        Logger.error(s"Native Error - Request NINO $requestNino does not match authority NINO $authNino! Response is ${response.value}")
        Unauthorized
      } else if (!nino.equals(authNino) || !requestNino.value.take(nino_compare_length).equals(nino)) {
        Logger.error(s"Native Error - Failed to match tax summary response NINO $ninoCheck with authority NINO $authNino! Response is ${response.value}")
        Unauthorized
      } else success
    } else success

    Future.successful(result)
  }
}

@Singleton
class LiveOrchestrationController  @Inject()(
  override val appNameConfiguration: Configuration,
  override val auditConnector: AuditConnector,
  override val authConnector: AuthConnector,
  override val service: LiveOrchestrationService,
  override val actorSystem: ActorSystem,
  override val lifecycle: ApplicationLifecycle,
  val reactiveMongo: Provider[ReactiveMongoComponent],
  @Named("supported.generic.service.max") override val serviceMax: Int,
  @Named("supported.generic.event.max") override val eventMax: Int,
  @Named("controllers.confidenceLevel") override val confLevel: Int,
  @Named("poll.success.maxAge") override val maxAgeForSuccess: Int ) extends NativeAppsOrchestrationController {

  override val app: String = "Live-Orchestration-Controller"
  override lazy val repository: AsyncRepository = new AsyncMongoRepository()(reactiveMongo.get().mongoConnector.db)
}
