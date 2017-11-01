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

package uk.gov.hmrc.ngc.orchestration.services

import java.util.UUID

import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.config.ConfiguredCampaigns
import uk.gov.hmrc.ngc.orchestration.connectors._
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.ngc.orchestration.executors.ExecutorFactory
import uk.gov.hmrc.ngc.orchestration.services.live.{MFAAPIResponse, MFAIntegration, MFARequest}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._

case class OrchestrationServiceRequest(requestLegacy: Option[JsValue], request: Option[OrchestrationRequest])

case class DeviceVersion(os : String, version : String)

object DeviceVersion {
  implicit val formats = Json.format[DeviceVersion]
}

case class PreFlightRequest(os: String, version:String, mfa:Option[MFARequest])

object PreFlightRequest {
  implicit val mfa = MFARequest.formats
  implicit val formats = Json.format[PreFlightRequest]
}

case class JourneyRequest(userIdentifier: String, continueUrl: String, origin: String, affinityGroup: String, context: String, serviceUrl: Option[String], scopes: Seq[String])

object JourneyRequest {
  implicit val format = Json.format[JourneyRequest]
}

case class JourneyResponse(journeyId: String, userIdentifier: String, registrationId: Option[String], continueUrl: String, origin: String, affinityGroup: String, registrationSkippable: Boolean, factor: Option[String], factorUri: Option[String], status: String, createdAt: DateTime)

object JourneyResponse {
  implicit val format = Json.format[JourneyResponse]
}

case class ServiceState(state:String, func: Accounts => MFARequest => Option[String] => Future[MFAAPIResponse])


trait OrchestrationService {

  def preFlightCheck(request:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[PreFlightCheckResponse] = ???

  def startup(inputRequest:JsValue, nino: Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier): Future[JsObject] = ???

  def orchestrate(request: OrchestrationServiceRequest, nino: Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier): Future[JsObject] = ???
}

@Singleton
class LiveOrchestrationService @Inject()(mfaIntegration: MFAIntegration,
                                         genericConnector: GenericConnector,
                                         override val auditConnector: AuditConnector,
                                         override val authConnector: AuthConnector,
                                         @Named("confidenceLevel") override val confLevel: Int)
  extends OrchestrationService with Authorisation with ExecutorFactory with Auditor with ConfiguredCampaigns {

  override def preFlightCheck(request:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[PreFlightCheckResponse] = {
    withAudit("preFlightCheck", Map.empty) {
      for {
        accounts <- getAccounts(journeyId)
        mfaOutcome <- mfaDecision(accounts, request.mfa, journeyId)
        versionUpdate <- getVersion(journeyId, request.os, request.version)
      } yield {
        val mfaURI: Option[MfaURI] = mfaOutcome.fold(Option.empty[MfaURI]){ _.mfa}
        // If authority has been updated then override the original accounts response from auth.
        val returnAccounts = mfaOutcome.fold(accounts) { found =>
          if (found.authUpdated)
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
    grantAccess(nino)
    request match {
      case OrchestrationServiceRequest(None, Some(request)) ⇒
        buildAndExecute(request, nino.value, journeyId).map(obj ⇒ Json.obj("OrchestrationResponse" → obj))
      case OrchestrationServiceRequest(Some(legacyRequest), None) ⇒
        startup(legacyRequest, nino, journeyId)
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
      PushRegistration(genericConnector, inputRequest, journeyId)
    ).map(item => item.execute(nino, year))

    for (results <- sequence(futuresSeq).map(_.flatten)) yield {
      val response = results.map(b => Json.obj(b.id -> b.jsValue))
      val campaigns = configuredCampaigns(hasData, response.foldLeft(Json.obj())((obj, a) => obj.deepMerge(a.as[JsObject])))
      response ++ (if(!campaigns.isEmpty) Seq(Json.obj("campaigns" -> Json.toJson(campaigns))) else Seq.empty)
    }
  }


  private def mfaDecision(accounts:Accounts, mfa: Option[MFARequest], journeyId: Option[String])(implicit hc: HeaderCarrier) : Future[Option[MFAAPIResponse]] = {
    def mfaNotRequired = Future.successful(Option.empty[MFAAPIResponse])
    if (!accounts.routeToTwoFactor)
      mfaNotRequired
    else mfa.fold(mfaNotRequired) { mfa ⇒
      mfaIntegration.verifyMFAStatus(mfa, accounts, journeyId).map(item ⇒ Some(item))
    }
  }

  private def getVersion(journeyId: Option[String], os: String, version: String)(implicit hc: HeaderCarrier) = {
    def buildJourney = journeyId.fold("")(id ⇒ s"?journeyId=$id")
    val device = DeviceVersion(os, version)
    val path = s"/profile/native-app/version-check$buildJourney"
    genericConnector.doPost(Json.toJson(device), "customer-profile", path, hc).map {
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
class SandboxOrchestrationService extends OrchestrationService with FileResource with ExecutorFactory {

  val cache: scala.collection.mutable.Map[String, String] = scala.collection.mutable.Map()

  val defaultUser = "404893573708"
  val defaultNino = "CS700100A"
  private val ninoMapping = Map(defaultUser -> defaultNino,
                                "404893573709" -> "CS700101A",
                                "404893573710" -> "CS700102A",
                                "404893573711" -> "CS700103A",
                                "404893573712" -> "CS700104A",
                                "404893573713" -> "CS700105A",
                                "404893573714" -> "CS700106A",
                                "404893573715" -> "CS700107A",
                                "404893573716" -> "CS700108A",
                                "404893573717" -> "CS700109A")

  override def preFlightCheck(preflightRequest:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[PreFlightCheckResponse] = {
    successful(hc.extraHeaders.find(_._1 equals "X-MOBILE-USER-ID") match {
      case  Some((_, value))  => buildPreFlightResponse(value)
      case _ => buildPreFlightResponse(defaultUser)
    })
  }

  override def startup(jsValue:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier): Future[JsObject] = {
    successful(Json.obj("status" -> Json.obj("code" -> "poll")))
  }


  def recordGenericServiceExecution(orchestrationRequest: OrchestrationRequest, journeyId: Option[String])(implicit hc: HeaderCarrier) = {
    val eventRequest = for {
      request ← orchestrationRequest.eventRequest.getOrElse(Seq.empty[ExecutorRequest])
    } yield (request.name)
    val serviceRequest = for {
      request ← orchestrationRequest.serviceRequest.getOrElse(Seq.empty[ExecutorRequest])
    } yield (request.name)
    val all = serviceRequest.union(eventRequest)
    cache.put(journeyId.getOrElse("genericExecution"), all.mkString("|"))
  }

  override def orchestrate(request: OrchestrationServiceRequest, nino: Nino, journeyId: Option[String])(implicit hc: HeaderCarrier): Future[JsObject] = {
    request match {
      case OrchestrationServiceRequest(None, Some(orchestrationRequest)) ⇒  {
        recordGenericServiceExecution(orchestrationRequest, journeyId)(hc)
        successful(Json.obj("status" → Json.obj("code" → "poll")))
      }
      case OrchestrationServiceRequest(Some(legacyRequest), None) ⇒ successful(Json.obj("status" → Json.obj("code" → "poll")))
      case _ ⇒ Future.successful(Json.obj("status" → Json.obj("code" → "error")))
    }
  }

  def getGenericExecutions(journeyId: Option[String]) : Option[String] = {
    val response = cache.get(journeyId.getOrElse("genericExecution"))
    cache.remove(journeyId.getOrElse("genericExecution"))
    response
  }

  private def buildPreFlightResponse(userId: String) : PreFlightCheckResponse = {
    val nino = Nino(ninoMapping.getOrElse(userId, defaultNino))
    PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(nino), None, routeToIV = false, routeToTwoFactor = false, UUID.randomUUID().toString, "credId-1234", "Individual"))
  }
}