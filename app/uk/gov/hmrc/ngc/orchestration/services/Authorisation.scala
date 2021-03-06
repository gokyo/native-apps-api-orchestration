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

import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.controllers.{AccountWithLowCL, FailToMatchTaxIdOnAuth, NinoNotFoundOnAccount}
import uk.gov.hmrc.ngc.orchestration.domain.Accounts

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Authority(nino: Nino)

trait Confidence {
  val confLevel: Int
}

trait Authorisation extends AuthorisedFunctions with Confidence {

  self: Confidence ⇒

  def getAccounts(journeyId: Option[String])(implicit hc: HeaderCarrier) = {
    authorised()
      .retrieve(nino and saUtr and affinityGroup and credentials and confidenceLevel) {
      case nino ~ saUtr ~ affinityGroup ~ credentials ~ confidenceLevel ⇒ {
        val routeToIV = confLevel > confidenceLevel.level
        val journeyIdentifier = journeyId.filter(id ⇒ id.length > 0).getOrElse(randomUUID().toString)
        if (credentials.providerType != "GovernmentGateway") throw new UnsupportedAuthProvider
        Future(Accounts(nino.map(Nino(_)), saUtr.map(SaUtr(_)), routeToIV,
          journeyIdentifier, credentials.providerId, affinityGroup.get.toString))
      }
    }
  }

  def grantAccess(requestedNino: Nino)(implicit hc: HeaderCarrier) = {
    lazy val ninoNotFoundOnAccount = new NinoNotFoundOnAccount
    lazy val failedToMatchNino = new FailToMatchTaxIdOnAuth
    lazy val lowConfidenceLevel = new AccountWithLowCL

    authorised(Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", requestedNino.value)), "Activated", None))
      .retrieve(nino and confidenceLevel) {
        case Some(nino) ~ confidenceLevel ⇒ {
          if (nino.isEmpty) throw ninoNotFoundOnAccount
          if (!nino.equals(requestedNino.nino)) throw failedToMatchNino
          if (confLevel > confidenceLevel.level) throw lowConfidenceLevel
          Future(Authority(Nino(nino)))
        }
        case None ~ _ ⇒ {
          throw ninoNotFoundOnAccount
        }
      }
  }
}
