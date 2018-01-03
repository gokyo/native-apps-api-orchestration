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

package uk.gov.hmrc.ngc.orchestration.executors

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.{Configuration, Logger, LoggerLike, Play}
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier}
import uk.gov.hmrc.ngc.orchestration.config.MicroserviceAuditConnector
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{Audit, DataEvent}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


sealed trait Executor[T >: ExecutorResponse] {
  val executionType: String
  val executorName: String
  val cacheTime: Option[Long]

  def execute(cacheTime: Option[Long], data: Option[JsValue], nino: String, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[T]]
}

trait ServiceExecutor extends Executor[ExecutorResponse] {
  val serviceName: String
  val POST = "POST"
  val GET = "GET"
  val cacheTime: Option[Long]
  lazy val host: String = getConfigProperty("host")
  lazy val port: Int = getConfigProperty("port").toInt

  def connector: GenericConnector

  def path(journeyId: Option[String], nino: String, data: Option[JsValue]): String

  override def execute(cacheTime: Option[Long], data: Option[JsValue], nino: String, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[ExecutorResponse]] = {
    executionType.toUpperCase match {
      case POST =>
        val postData = data.getOrElse(throw new Exception("No Post Data Provided!"))
        val result = connector.doPost[JsValue](postData, serviceName, path(journeyId, nino, data), hc)
        result.map { response =>
          Some(ExecutorResponse(executorName, Option(response), cacheTime, Some(false)))
        }
      case GET =>
        connector.doGet(serviceName, path(journeyId, nino, data), hc).map {
          response => {
            Some(ExecutorResponse(executorName, Option(response), cacheTime, Some(false)))
          }
        }

      case _ => throw new Exception("Method not supported : " + executionType)
    }
  }

  private def getServiceConfig: Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception(s"No micro services configured for $serviceName."))
  }

  private def getConfigProperty(property: String): String = {
    getServiceConfig.getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }

  def buildJourneyQueryParam(journeyId: Option[String]) = journeyId.fold("")(id => s"?journeyId=$id")
}

trait EventExecutor extends Executor[ExecutorResponse]

trait ExecutorFactory {

  val genericConnector: GenericConnector
  val auditConnector: AuditConnector

  val feedback = DeskProFeedbackExecutor(genericConnector)
  val versionCheck = VersionCheckExecutor(genericConnector)
  val pushNotificationGetMessageExecutor = PushNotificationGetMessageExecutor(genericConnector)
  val pushNotificationRespondToMessageExecutor = PushNotificationRespondToMessageExecutor(genericConnector)
  val nativeAppSurveyWidget = WidgetSurveyDataServiceExecutor(genericConnector)
  val claimantDetailsServiceExecutor = ClaimantDetailsServiceExecutor(genericConnector)

  val auditEventExecutor = AuditEventExecutor(audit = new Audit("native-apps", auditConnector))

  val serviceExecutors: Map[String, ServiceExecutor] = Map(
    Seq(
      versionCheck,
      feedback,
      pushNotificationGetMessageExecutor,
      pushNotificationRespondToMessageExecutor,
      nativeAppSurveyWidget,
      claimantDetailsServiceExecutor
    ).map(executor => executor.executorName -> executor): _*
  )

  val eventExecutors: Map[String, EventExecutor] = Map(auditEventExecutor.executorName -> auditEventExecutor)

  def buildAndExecute(orchestrationRequest: OrchestrationRequest, nino: String, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[OrchestrationResponse] = {
    for {
      serviceResponse <- {
        if (orchestrationRequest.serviceRequest.isDefined) {
          execute[ExecutorResponse](orchestrationRequest.serviceRequest.get, nino, journeyId, serviceExecutors).map(Option(_))
        }
        else Future(None)
      }
      eventResponse <- {
        if (orchestrationRequest.eventRequest.isDefined) {
          execute[ExecutorResponse](orchestrationRequest.eventRequest.get, nino, journeyId, eventExecutors).map(Option(_))
        }
        else Future(None)
      }
    } yield (OrchestrationResponse(serviceResponse, eventResponse))
  }

  private def execute[T >: ExecutorResponse](request: Seq[ExecutorRequest], nino: String, journeyId: Option[String], executors: Map[String, Executor[T]])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Seq[T]] = {
    val futuresSeq: Seq[Future[Option[T]]] = request.map {
      request => (executors.get(request.name), request.data)
    }.map(item => item._1.get.execute(item._1.get.cacheTime, item._2, nino, journeyId)
      .recover {
        case ex: GatewayTimeoutException => {
          Some(ExecutorResponse(item._1.get.executorName, None, None, Some(true), Some(true)))
        }
        case ex: Exception => {
          Logger.error(s"Failed to execute ${item._1.get.executorName} with exception ${ex.getMessage}!")
          Some(ExecutorResponse(item._1.get.executorName, None, None, Some(true), Some(false)))
        }
      })
    Future.sequence(futuresSeq).map(item => item.flatten)
  }

}

case class VersionCheckExecutor(genericConnector: GenericConnector) extends ServiceExecutor {
  override val executorName: String = "version-check"

  override val executionType: String = POST
  override val serviceName: String = "customer-profile"

  override def path(journeyId: Option[String], nino: String, data: Option[JsValue]) = "/profile/native-app/version-check"

  override def connector: GenericConnector = genericConnector

  override val cacheTime: Option[Long] = None
}

case class DeskProFeedbackExecutor(genericConnector: GenericConnector) extends ServiceExecutor {
  override val executorName: String = "deskpro-feedback"

  override val executionType: String = POST
  override val serviceName: String = "deskpro-feedback"

  override def path(journeyId: Option[String], nino: String, data: Option[JsValue]) = "/deskpro/feedback"

  override val cacheTime: Option[Long] = None

  override def connector = genericConnector
}

case class PushNotificationGetMessageExecutor(genericConnector: GenericConnector) extends ServiceExecutor {
  override val executorName: String = "push-notification-get-message"

  override val executionType: String = GET
  override val serviceName: String = "push-notification"

  override def path(journeyId: Option[String], nino: String, data: Option[JsValue]) = {
    val messageId = data.flatMap(json => (json \ "messageId").asOpt[String]).getOrElse(throw new Exception("No messageId provided"))

    s"/messages/$messageId${buildJourneyQueryParam(journeyId)}"
  }

  override val cacheTime: Option[Long] = None

  override def connector = genericConnector
}

case class PushNotificationRespondToMessageExecutor(genericConnector: GenericConnector) extends ServiceExecutor {
  override val executorName: String = "push-notification-respond-to-message"

  override val executionType: String = POST
  override val serviceName: String = "push-notification"

  override def path(journeyId: Option[String], nino: String, data: Option[JsValue]) = {
    val messageId = data.flatMap(json => (json \ "messageId").asOpt[String]).getOrElse(throw new Exception("No messageId provided"))

    s"/messages/$messageId/response${buildJourneyQueryParam(journeyId)}"
  }

  override val cacheTime: Option[Long] = None

  override def connector = genericConnector
}

case class AuditEventExecutor(audit: Audit = new Audit("native-apps", MicroserviceAuditConnector), logger: LoggerLike = Logger) extends EventExecutor {

  override val executorName: String = "ngc-audit-event"
  override val executionType: String = "EVENT"
  override val cacheTime: Option[Long] = None

  private case class ValidData(auditType: String, extraDetail: Map[String, String])

  override def execute(cacheTime: Option[Long], data: Option[JsValue], nino: String, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[ExecutorResponse]] = {
    val response = data.flatMap(validate).map { validData =>
      val defaultEvent = DataEvent(
        "native-apps",
        validData.auditType,
        tags = hc.toAuditTags("explicitAuditEvent", validData.auditType),
        detail = hc.toAuditDetails(validData.extraDetail.toSeq: _*)
      )

      val event: DataEvent = readGeneratedAt(data, defaultEvent.eventId)
        .map(generatedAt => defaultEvent.copy(generatedAt = generatedAt))
        .getOrElse(defaultEvent)

      audit.sendDataEvent(event)
      ExecutorResponse(executorName, failure = Some(false))
    }.getOrElse(
      ExecutorResponse(executorName, responseData = Some(Json.parse("""{"error": "Bad Request"}""")), failure = Some(true))
    )

    Future.successful(Some(response))
  }

  private def validate(data: JsValue): Option[ValidData] = {
    val maybeAuditType: Option[String] = (data \ "auditType").asOpt[String]
    val maybeNewFormatExtraDetail: Option[Map[String, String]] = (data \ "detail").asOpt[Map[String, String]]
    val maybeOldFormatExtraDetail: Option[Map[String, String]] = (data \ "nino").asOpt[String].map(nino => Map("nino" -> nino))
    val maybeExtraDetail: Option[Map[String, String]] = maybeNewFormatExtraDetail.orElse(maybeOldFormatExtraDetail)

    for {
      auditType <- maybeAuditType
      extraDetail <- maybeExtraDetail
    } yield ValidData(auditType, extraDetail)
  }

  private def readGeneratedAt(data: Option[JsValue], eventId: String): Option[DateTime] = {
    import uk.gov.hmrc.ngc.orchestration.json.IsoDateTimeReads.isoDateTimeReads

    data
      .flatMap(json => (json \ "generatedAt").asOpt[JsValue])
      .flatMap { generatedAtJsValue: JsValue =>
        generatedAtJsValue.validate[DateTime](isoDateTimeReads) match {
          case JsSuccess(dateTime, _) => Some(dateTime)
          case _: JsError =>
            logger.warn(s"""Couldn't parse generatedAt timestamp $generatedAtJsValue, defaulting to now for audit event $eventId""")
            None
        }
      }
  }
}

case class WidgetSurveyDataServiceExecutor(genericConnector: GenericConnector) extends ServiceExecutor {
  override val serviceName: String = "native-app-widget"
  override val cacheTime: Option[Long] = None
  override val executionType: String = POST
  override val executorName: String = "survey-widget"

  override def connector = genericConnector

  override def path(journeyId: Option[String], nino: String, data: Option[JsValue]): String = s"/native-app-widget/${nino}/widget-data"
}

case class ClaimantDetailsServiceExecutor(genericConnector: GenericConnector) extends ServiceExecutor {
  override val serviceName: String = "personal-income"
  override val cacheTime: Option[Long] = None

  override def connector = genericConnector

  override val executorName: String = "claimant-details"

  override def path(journeyId: Option[String], nino: String, data: Option[JsValue]): String = ""

  override val executionType: String = ""

  override def execute(cacheTime: Option[Long], data: Option[JsValue], nino: String, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[ExecutorResponse]] = {

    val journeyParam = journeyId.map(id => s"&journeyId=$id").getOrElse("")

    val claimsPath = s"/income/$nino/tax-credits/claimant-details?claims=true$journeyParam"

    val claimantDetails = for (
      references <- connector.doGet(serviceName, claimsPath, hc);
      tokens <- getTokens(nino, getBarcodes(references), journeyId);
      details <- getDetails(nino, references, tokens, journeyId)
    ) yield details

    claimantDetails.map { response =>
      Some(ExecutorResponse(executorName, Option(response), cacheTime, failure = Some(false)))
    }
  }

  def getBarcodes(references: JsValue): Seq[String] = {
    (references \ "references" \\ "barcodeReference").flatMap(_.asOpt[String])
  }

  def getTokens(nino: String, barcodes: Seq[String], journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Map[String, String]] = {
    val journeyParam = journeyId.map(id => s"?journeyId=$id").getOrElse("")

    val validBarcodes = barcodes.filter(!_.equals("000000000000000"))
    val tokens = Future.sequence(validBarcodes.map { code =>

        val path = s"/income/$nino/tax-credits/$code/auth$journeyParam"
        val result = connector.doGet(serviceName, path, hc)

        result.map(token => (code, (token \ "tcrAuthToken").asOpt[String]))
          .filter(_._2.nonEmpty)
          .map(token => (token._1, token._2.get))
    })
    tokens.recover {
      case ex: Exception =>
        Logger.error("failed to get tcrAuthTokens: " + ex.getMessage)
        Seq.empty
    }.map(_.toMap)
  }

  def getDetails(nino: String, references: JsValue, tokens: Map[String, String], journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsValue] = {
    val journeyParam = journeyId.map(id => s"?journeyId=$id").getOrElse("")

    val renewalFormType = Future.sequence(tokens.map { case (code, auth) =>
      val path = s"/income/$nino/tax-credits/claimant-details$journeyParam"
      val result = connector.doGet(serviceName, path, hc.withExtraHeaders(("tcrAuthToken", auth)))
      result.map(details => (code, (details \ "renewalFormType").asOpt[String]))
    })

    renewalFormType.recover {
      case ex: Exception =>
        Logger.error("failed to get claimant-details: " + ex.getMessage)
        Seq(("_NO_BAR_CODE", None))
    }.map{ renewals =>
      renewals.foldLeft(references) { case (doc, detailsForCode) =>
        (doc \ "references").asOpt[JsArray].map { array =>
          val updated = JsArray(array.value.flatMap { reference =>
            (reference \\ "barcodeReference").map { c =>
              if (c.asOpt[String] == Some(detailsForCode._1)) {
                detailsForCode._2.map { details =>
                  val transformer = (__ \ "renewal").json.update(
                    __.read[JsObject].map {
                      _ ++ Json.obj("renewalFormType" -> details)
                    }
                  )
                  reference.transform(transformer).get
                }.getOrElse(reference)
              } else {
                reference
              }
            }
          })

          JsObject(Seq("references" -> updated))
        }.getOrElse(doc)
      }
    }
  }
}
