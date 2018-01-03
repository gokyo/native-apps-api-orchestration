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

package uk.gov.hmrc.ngc.orchestration.config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.api.config.{ServiceLocatorConfig, ServiceLocatorRegistration}
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.msasync.config.CookieSessionFilter
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.filters.{MicroserviceFilterSupport, NoCacheFilter}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {

  override val auditConnector = MicroserviceAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with ServiceLocatorConfig with ServiceLocatorRegistration {

  override val auditConnector: AuditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig("microservice.metrics")

  override val loggingFilter: LoggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter: AuditFilter = MicroserviceAuditFilter

  override val authFilter: Option[EssentialFilter] = None

  private lazy val sessionFilter = CookieSessionFilter.SessionCookieFilter

  override val slConnector: ServiceLocatorConnector = ServiceLocatorConnector(WSHttp)

  override implicit val hc: HeaderCarrier = HeaderCarrier()

  override def microserviceFilters: Seq[EssentialFilter] = Seq.empty

  override def doFilter(a: EssentialAction): EssentialAction = {
    // Note: Add the session filter to the controller in order for session cookie handling.
    Filters(super.doFilter(a), defaultMicroserviceFilters.filterNot( _.isInstanceOf[NoCacheFilter.type] ) ++ sessionFilter : _*)
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {
    super.onError(request, ex) map (res => {
      res.header.status
      match {
        case 401 => Status(ErrorUnauthorized.httpStatusCode)(Json.toJson(ErrorUnauthorized))
        case _ => Status(ErrorInternalServerError.httpStatusCode)(Json.toJson(ErrorInternalServerError))
      }
    })
  }

  override def onBadRequest(request: RequestHeader, error: String): Future[Result] = {
    val errorScenario = error match {
      case _ => ErrorGenericBadRequest(error)
    }
    Future.successful(Status(errorScenario.httpStatusCode)(Json.toJson(errorScenario)))
  }
  override def onHandlerNotFound(request: RequestHeader): Future[Result] = Future.successful(NotFound(Json.toJson(ErrorNotFound)))
}
