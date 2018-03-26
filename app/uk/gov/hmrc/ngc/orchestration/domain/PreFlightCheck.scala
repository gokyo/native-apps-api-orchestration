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

package uk.gov.hmrc.ngc.orchestration.domain

import play.api.libs.json._
import uk.gov.hmrc.domain.{Nino, SaUtr}
import play.api.libs.functional.syntax._

case class PreFlightCheckResponse(upgradeRequired: Boolean, accounts: Accounts)

case class Browser()

object PreFlightCheckResponse {

  implicit val accountsFmt = Accounts.formats

  implicit val preFlightCheckResponseFmt = Json.format[PreFlightCheckResponse]
}

case class Accounts(nino: Option[Nino], saUtr: Option[SaUtr], routeToIV : Boolean, journeyId: String, credId:String,
                    affinityGroup:String, @Deprecated routeToTwoFactor: Boolean = false)

object Accounts {
  implicit val reads = (
    (JsPath \ "nino").readNullable[Nino] and
      (JsPath \ "saUtr").readNullable[SaUtr] and
      (JsPath \ "routeToIV").read[Boolean] and
      (JsPath \ "journeyId").read[String] and
      (JsPath \ "credId").read[String] and
      (JsPath \ "affinityGroup").read[String] and
      (JsPath \ "routeToTwoFactor").read[Boolean]
    ) (Accounts.apply _)

    implicit val writes = new Writes[Accounts] {
      def withNino(nino: Option[Nino]) = nino.fold(Json.obj()){found => Json.obj("nino" -> found.value)}
      def withSaUtr(saUtr: Option[SaUtr]) = saUtr.fold(Json.obj()){found => Json.obj("saUtr" -> found.value)}

      def writes(accounts: Accounts) = {
        withNino(accounts.nino) ++ withSaUtr(accounts.saUtr) ++ Json.obj(
          "routeToIV" -> accounts.routeToIV,
          "routeToTwoFactor" -> accounts.routeToTwoFactor,
          "journeyId" -> accounts.journeyId)
      }
    }

    implicit val formats = Format(reads, writes)
}

