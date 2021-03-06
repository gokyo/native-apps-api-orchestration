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

import play.api.libs.json._
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.controllers.ResponseCode

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait Executor {
  val id: String
  val serviceName: String

  def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]]

  def logJourneyId(journeyId: Option[String]) = s"Native Error - ${journeyId.fold("no Journey id supplied")(id => id)}"

  def buildJourneyQueryParam(journeyId: Option[String]): String = journeyId.fold("")(id => s"?journeyId=$id")
}

case class TaxSummary(connector: GenericConnector, journeyId: Option[String]) extends Executor with ResponseCode {
  override val id  = "taxSummary"
  override val serviceName: String = "personal-income"

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(serviceName, s"/income/$nino/tax-summary/$year${buildJourneyQueryParam(journeyId)}", hc).map(res => {
      Some(Result(id, res))
    }).recover {
      case ex: Exception =>
        Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve the tax-summary data and exception is ${ex.getMessage}!")
        // An empty JSON object indicates failed to retrieve the tax-summary.
        Some(Result(id, Json.obj()))
    }
  }
}

case class TaxCreditsRenewals(connector: GenericConnector, journeyId: Option[String]) extends Executor {
  override val id = "taxCreditRenewals"
  override val serviceName = "personal-income"
  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(
      serviceName, s"/income/tax-credits/submission/state/enabled${buildJourneyQueryParam(journeyId)}", hc).map(res =>
        Some(Result(id, JsObject(Seq("submissionsState" -> JsString(res.\("submissionsState").as[String])))))
    ).recover {
      case ex: Exception =>
        Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve TaxCreditsRenewals and exception is ${ex.getMessage}! Default of submissionsState is error!")
        Some(Result(id, JsObject(Seq("submissionsState" -> JsString(value = "error")))))
    }
  }
}

case class TaxCreditSummary(connector: GenericConnector, journeyId: Option[String]) extends Executor {
  override val id: String = "taxCreditSummary"
  override val serviceName: String = "personal-income"

  def decision(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Boolean]] = {

    def taxCreditDecision(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
      Logger.info(s"decision: HC received is ${hc.authorization} for Journey Id $journeyId")

      connector.doGet(serviceName, s"/income/$nino/tax-credits/tax-credits-decision${buildJourneyQueryParam(journeyId)}", hc).map(res => {
        Some(Result("decision", res))
      }).recover {
        case ex: uk.gov.hmrc.http.NotFoundException =>
          Logger.warn(s"${logJourneyId(journeyId)} - 404 returned for tax-credits-decision.")
          throw ex

        case ex: Upstream4xxResponse =>
          handle4xxException(ex)
          throw ex

        case ex: Exception =>
          Logger.warn(s"${logJourneyId(journeyId)} - Failed to retrieve tax-credits-decision and exception is ${ex.getMessage}!")
          throw ex
      }
    }

    taxCreditDecision(nino).map {
      case Some(result) => Some((result.jsValue \ "showData").as[Boolean])
      case _ => None
    }
  }

  def getTaxCreditSummary(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(serviceName, s"/income/$nino/tax-credits/tax-credits-summary${buildJourneyQueryParam(journeyId)}", hc).map(r => {
      Some(Result(id, r))
    }).recover {

      case ex: Upstream4xxResponse =>
        handle4xxException(ex)
        None

      case ex: Exception =>
        Logger.warn(s"${logJourneyId(journeyId)} - Failed to retrieve tax-credits/tax-credits-summary and exception is ${ex.getMessage}!")
        None
    }
  }

  private def handle4xxException(ex:Upstream4xxResponse)(implicit hc: HeaderCarrier): Unit = {
    if (ex.upstreamResponseCode==401) {
      Logger.warn(s"${logJourneyId(journeyId)} - 401 returned for tax-credits-decision!")
    } else {
      Logger.warn(s"${logJourneyId(journeyId)} - ${ex.upstreamResponseCode} returned for tax-credits-decision.")
    }
  }

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    val customerEligibleTaxCreditsDefault = Some(Result(id, Json.obj()))

    def getSummaryData(decision:Option[Boolean]) : Future[Option[Result]] = {
      decision match {
        case Some(value:Boolean) if value =>
          // OK to retrieve the tax-credit-summary, default on failure.
          getTaxCreditSummary(nino).map( res => res.fold(customerEligibleTaxCreditsDefault){ summary => Some(summary) })

        case Some(value:Boolean) if !value =>
          // Tax-Credit-Summary tab can be displayed but no summary data returned.
          Future.successful(customerEligibleTaxCreditsDefault)

        case _ =>  Future.successful(None) // No data to return
      }
    }

    val decisionF = decision(nino)

    (for {
          decision <- decisionF
          taxCreditSummary <- getSummaryData(decision)
        } yield taxCreditSummary)
    .recover {
        case ex: Exception =>
          Logger.warn(s"${logJourneyId(journeyId)} - Failed to orchestrate TaxCreditSummary. Exception is ${ex.getMessage}!")
          None
    }
  }
}

case class HelpToSaveStartup(logger: LoggerLike, connector: GenericConnector) extends Executor {
  override val id: String = "helpToSave"
  override val serviceName: String = "mobile-help-to-save"

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] =
    connector.doGet(serviceName, "/mobile-help-to-save/startup", hc) map { json =>
      Some(Result(id, json))
    } recover {
      case NonFatal(e) =>
        logger.warn(s"""Exception thrown by "/mobile-help-to-save/startup", not returning any $id result""", e)
        None
    }
}

case class Mandatory() extends Exception

case class Result(id: String, jsValue: JsValue)
