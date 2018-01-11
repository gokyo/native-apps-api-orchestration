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

package uk.gov.hmrc.ngc.orchestration.services

import java.util.UUID.randomUUID

import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.{Configuration, Logger}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.config.ConfiguredCampaigns
import uk.gov.hmrc.ngc.orchestration.connectors._
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.ngc.orchestration.executors.ExecutorFactory
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

case class OrchestrationServiceRequest(requestLegacy: Option[JsValue], request: Option[OrchestrationRequest])

case class DeviceVersion(os : String, version : String)

object DeviceVersion {
  implicit val formats: Format[DeviceVersion] = Json.format[DeviceVersion]
}

case class PreFlightRequest(os: String, version:String, mfa:Option[MFARequest])

object PreFlightRequest {
  implicit val mfa: Format[MFARequest] = MFARequest.formats
  implicit val formats: Format[PreFlightRequest] = Json.format[PreFlightRequest]
}

trait OrchestrationService {

  def preFlightCheck(request:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[PreFlightCheckResponse]

  def startup(inputRequest:JsValue, nino: Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier): Future[JsObject]

  def orchestrate(request: OrchestrationServiceRequest, nino: Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier): Future[JsObject]
}

@Singleton
class LiveOrchestrationService @Inject()(mfaIntegration: MFAIntegration,
                                         executorFactory: ExecutorFactory,
                                         genericConnector: GenericConnector,
                                         val appNameConfiguration: Configuration,
                                         val auditConnector: AuditConnector,
                                         val authConnector: AuthConnector,
                                         @Named("controllers.confidenceLevel") val confLevel: Int,
                                         @Named("routeToTwoFactorAlwaysFalse") val routeToTwoFactorAlwaysFalse: Boolean)
  extends OrchestrationService with Authorisation with Auditor with ConfiguredCampaigns {

  override def preFlightCheck(request:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[PreFlightCheckResponse] = {
    withAudit("preFlightCheck", Map.empty) {
      for {
        accounts <- getAccounts(journeyId)
        mfaOutcome <- mfaIntegration.mfaDecision(accounts, request.mfa, journeyId)
        versionUpdate <- getVersion(journeyId, request.os, request.version)
      } yield {
        val mfaURI: Option[MfaURI] = mfaOutcome.fold(Option.empty[MfaURI]){ _.mfa}
        // If authority has been updated then override the original accounts response from auth.
        val returnAccounts = mfaOutcome.fold(accounts) { found =>
          if (routeToTwoFactorAlwaysFalse || found.authUpdated)
            accounts.copy(routeToTwoFactor = false)
          else {
            accounts.copy(routeToTwoFactor = found.routeToTwoFactor)
          }
        }
        PreFlightCheckResponse(versionUpdate, returnAccounts, mfaURI)
      }
    }
  }

  override def orchestrate(request: OrchestrationServiceRequest, nino: Nino, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[JsObject] = {
    grantAccess(nino).flatMap { _ ⇒
      request match {
        case OrchestrationServiceRequest(None, Some(request)) ⇒
          executorFactory.buildAndExecute(request, nino.value, journeyId).map(obj ⇒ Json.obj("OrchestrationResponse" → obj))
        case OrchestrationServiceRequest(Some(legacyRequest), None) ⇒
          startup(legacyRequest, nino, journeyId)
      }
    }
  }

  override def startup(inputRequest:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[JsObject]= {
    withAudit("startup", Map("nino" -> nino.value)) {
      val year = TaxYear.current.currentYear

      buildResponse(inputRequest:JsValue, nino.value, year, journeyId).map(item => item).map(r => r.foldLeft(Json.obj())((b, a) => b ++ a)).recover {
        case ex:Exception =>
          Logger.error(s"Native Error - failure with processing startup for $journeyId. Exception is $ex")
          throw ex
      }
    }
  }

  private def buildResponse(inputRequest:JsValue, nino: String, year: Int, journeyId: Option[String])(implicit hc: HeaderCarrier) : Future[Seq[JsObject]] = {
    val futuresSeq: Seq[Future[Option[Result]]] = Seq(
      TaxSummary(genericConnector, journeyId),
      TaxCreditSummary(genericConnector, journeyId),
      TaxCreditsSubmissionState(genericConnector, journeyId),
      TaxCreditsRenewals(genericConnector, journeyId),
      PushRegistration(genericConnector, inputRequest, journeyId),
      HelpToSaveStartup(Logger, genericConnector)
    ).map(item => item.execute(nino, year))

    for (results <- sequence(futuresSeq).map(_.flatten)) yield {
      val response = results.map(b => Json.obj(b.id -> b.jsValue))
      val campaigns = configuredCampaigns(hasData, response.foldLeft(Json.obj())((obj, a) => obj ++ a))
      response ++ (if(campaigns.nonEmpty) Seq(Json.obj("campaigns" -> Json.toJson(campaigns))) else Seq.empty)
    }
  }


  private def getVersion(journeyId: Option[String], os: String, version: String)(implicit hc: HeaderCarrier) = {
    def buildJourney = journeyId.fold("")(id ⇒ s"?journeyId=$id")
    val device = DeviceVersion(os, version)
    val path = s"/profile/native-app/version-check$buildJourney"
    genericConnector.doPost[JsValue](Json.toJson(device), "customer-profile", path, hc).map {
      resp ⇒ (resp \ "upgrade").as[Boolean]
    }.recover{
      // Default to false - i.e. no upgrade required.
      case exception:Exception =>
        Logger.error(s"Native Error - failure with processing version check. Exception is $exception")
        false
    }
  }

}

@Singleton
class SandboxOrchestrationService @Inject() (genericConnector: GenericConnector, executorFactory: ExecutorFactory)
  extends OrchestrationService with FileResource {

  val cache: scala.collection.mutable.Map[String, String] = scala.collection.mutable.Map()

  override def preFlightCheck(preflightRequest:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[PreFlightCheckResponse] = {
    successful(buildPreFlightResponse)
  }

  override def startup(jsValue:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier): Future[JsObject] = {
    successful(Json.obj("status" -> Json.obj("code" -> "poll")))
  }


  private def recordGenericServiceExecution(orchestrationRequest: OrchestrationRequest, journeyId: Option[String])(implicit hc: HeaderCarrier) = {
    val eventRequest = for {
      request ← orchestrationRequest.eventRequest.getOrElse(Seq.empty[ExecutorRequest])
    } yield request.name
    val serviceRequest = for {
      request ← orchestrationRequest.serviceRequest.getOrElse(Seq.empty[ExecutorRequest])
    } yield request.name
    val all = serviceRequest.union(eventRequest)
    cache.put(journeyId.getOrElse("genericExecution"), all.mkString("|"))
  }

  override def orchestrate(request: OrchestrationServiceRequest, nino: Nino, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[JsObject] = {
    request match {
      case OrchestrationServiceRequest(None, Some(orchestrationRequest)) ⇒
        recordGenericServiceExecution(orchestrationRequest, journeyId)(hc)
        successful(Json.obj("status" → Json.obj("code" → "poll")))
      case OrchestrationServiceRequest(Some(_), None) ⇒ successful(Json.obj("status" → Json.obj("code" → "poll")))
      case _ ⇒ Future.successful(Json.obj("status" → Json.obj("code" → "error")))
    }
  }

  def getGenericExecutions(journeyId: Option[String]) : Option[String] = {
    val response = cache.get(journeyId.getOrElse("genericExecution"))
    cache.remove(journeyId.getOrElse("genericExecution"))
    response
  }

  private def buildPreFlightResponse() : PreFlightCheckResponse = {
    PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(Nino("CS700100A")), None, routeToIV = false,
      routeToTwoFactor = false, randomUUID().toString, "credId-1234", "Individual"))
  }

}