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

import play.api.libs.json.Json
import play.api.{Logger, mvc}
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, Upstream4xxResponse}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.http.HttpException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case object ErrorNinoInvalid extends ErrorResponse(400, "NINO_INVALID", "The provided NINO is invalid")

case object ErrorUnauthorizedNoNino extends ErrorResponse(401, "UNAUTHORIZED", "NINO does not exist on account")

case object ErrorUnauthorizedUpstream extends ErrorResponse(401, "UNAUTHORIZED", "Upstream service such as auth returned 401")

case object ErrorBadRequest extends ErrorResponse(400, "BAD_REQUEST", "Invalid POST request")

case object MandatoryResponse extends ErrorResponse(500, "MANDATORY", "Mandatory data not found")

case object ForbiddenAccess extends ErrorResponse(403, "UNAUTHORIZED", "Access denied!")

case object ErrorUnauthorizedWeakCredStrength extends ErrorResponse(401, "WEAK_CRED_STRENGTH", "Credential Strength on account does not allow access")

class BadRequestException(message:String) extends HttpException(message, 400)

class GrantAccessException(message: String) extends HttpException(message, 401)

class FailToMatchTaxIdOnAuth extends GrantAccessException("Unauthorised! Failure to match URL NINO against Auth NINO")

class NinoNotFoundOnAccount extends GrantAccessException("Unauthorised! NINO not found on account!")

class AccountWithLowCL extends GrantAccessException("Unauthorised! Account with low CL!")

class AccountWithWeakCredStrength(message:String) extends HttpException(message, 401)


trait ErrorHandling {
  self: BaseController =>
  val app:String

  def log(message:String) = Logger.info(s"$app $message")

  def errorWrapper(func: => Future[mvc.Result])(implicit hc: HeaderCarrier) = {

    func.recover {
      case ex: NotFoundException ⇒
        log("Resource not found!")
        Status(ErrorNotFound.httpStatusCode)(Json.toJson(ErrorNotFound))

      case ex:BadRequestException ⇒
        log("BadRequest!")
        Status(ErrorBadRequest.httpStatusCode)(Json.toJson(ErrorBadRequest))

      case ex: NinoNotFoundOnAccount ⇒
        Logger.info(ex.message)
        Unauthorized(Json.toJson(ErrorUnauthorizedNoNino))

      case ex: FailToMatchTaxIdOnAuth ⇒
        Logger.info(ex.message)
        Status(ErrorUnauthorized.httpStatusCode)(Json.toJson(ErrorUnauthorized))

      case ex: AccountWithLowCL ⇒
        Logger.info(ex.message)
        Unauthorized(Json.toJson(ErrorUnauthorizedLowCL))

      case ex: AccountWithWeakCredStrength ⇒
        Logger.info("Unauthorised! Account with weak cred strength!")
        Unauthorized(Json.toJson(ErrorUnauthorizedWeakCredStrength))

      case ex: Upstream4xxResponse if ex.upstreamResponseCode == 401 ⇒
        log("Upstream service returned 401")
        Status(ErrorUnauthorizedUpstream.httpStatusCode)(Json.toJson(ErrorUnauthorizedUpstream))

      case ex: AuthorisationException ⇒
        log("Unauthorised! Failure to authorise account or grant access")
        Unauthorized(Json.toJson(ErrorUnauthorizedUpstream))

      case e: Exception ⇒
        Logger.error(s"Native Error - $app Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(Json.toJson(ErrorInternalServerError))
    }

  }
}
