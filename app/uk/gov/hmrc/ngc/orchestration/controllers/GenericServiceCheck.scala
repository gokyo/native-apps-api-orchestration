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

import play.api.libs.json.JsSuccess
import play.api.mvc.{AnyContent, Request}
import play.api.{Play, mvc}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.domain.{ExecutorRequest, OrchestrationRequest}
import uk.gov.hmrc.ngc.orchestration.services.OrchestrationServiceRequest

import scala.concurrent.Future

trait GenericServiceCheck {

  self: NativeAppsOrchestrationController ⇒

  def validate(func: OrchestrationServiceRequest => Future[mvc.Result])(implicit request: Request[AnyContent], hc: HeaderCarrier) = {

    request.body.asJson.fold(throw new BadRequestException(s"Failed to build JSON payload! ${request.body}")){ json =>

      json.validate[OrchestrationRequest] match {
        case success: JsSuccess[OrchestrationRequest] ⇒ {
          val request = success.get
          if (invalid(verifyServiceName, request.serviceRequest).size > 0 || invalid(verifyEventName, request.eventRequest).size > 0) {
            Future.failed(new BadRequestException("Request not supported"))
          } else if (maxRequestsExceeded(request)) {
            Future.failed(new BadRequestException("Max Calls Exceeded"))
          } else if (!request.serviceRequest.isDefined && !request.eventRequest.isDefined) {
            Future.failed(new BadRequestException("Nothing to execute"))
          } else {
            func(OrchestrationServiceRequest(None, Some(success.get)))
          }
        }
        case _ ⇒ func(OrchestrationServiceRequest(Some(json), None))
      }
    }
  }

  private def maxRequestsExceeded(request: OrchestrationRequest) :Boolean = {
    request.serviceRequest.map{serviceMax < _.size}.getOrElse(false) || request.eventRequest.map(eventMax < _.size).getOrElse(false)
  }

  private def invalid(verify: ⇒ String ⇒ Boolean, request: Option[Seq[ExecutorRequest]]): Seq[Boolean] = {
    request.map(_.flatMap(req ⇒ if (!verify(req.name)) Some(true) else None)).getOrElse(Seq.empty)
  }

  protected def verifyServiceName(serviceName: String): Boolean = {
    Play.current.configuration.getBoolean(s"supported.generic.service.$serviceName.on").getOrElse(false)
  }

  protected def verifyEventName(eventName: String): Boolean = {
    Play.current.configuration.getBoolean(s"supported.generic.event.$eventName.on").getOrElse(false)
  }

}