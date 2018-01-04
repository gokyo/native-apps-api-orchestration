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

package uk.gov.hmrc.ngc.orchestration.json

import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.collection.Seq
import scala.util.control.Exception.catching

object IsoDateTimeReads {
  val isoDateTimeReads: Reads[DateTime] = new Reads[DateTime] {
    val df: DateTimeFormatter = ISODateTimeFormat.dateTimeParser

    def reads(json: JsValue): JsResult[DateTime] = json match {
      case JsString(s) => parseDate(s) match {
        case Some(d) => JsSuccess(d)
        case None => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.date.isoformat"))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.date"))))
    }

    private def parseDate(input: String): Option[DateTime] =
      catching[DateTime](classOf[IllegalArgumentException]) opt DateTime.parse(input, df).withZone(DateTimeZone.UTC)
  }

}
