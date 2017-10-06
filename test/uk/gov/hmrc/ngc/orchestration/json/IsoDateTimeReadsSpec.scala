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

package uk.gov.hmrc.ngc.orchestration.json

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{Matchers, WordSpec}
import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.collection.Seq

class IsoDateTimeReadsSpec extends WordSpec with Matchers {

  "isoDateTimeReads" should {
    import IsoDateTimeReads.isoDateTimeReads

    "read ISO datetimes with Z timezone" in {
      isoDateTimeReads.reads(JsString("2017-09-12T08:35:49.340Z")) shouldBe JsSuccess(new DateTime(2017, 9, 12, 8, 35, 49, 340, DateTimeZone.UTC))
    }

    "read ISO datetimes with an offset" in {
      isoDateTimeReads.reads(JsString("2017-09-12T09:35:49.340+01:00")) shouldBe JsSuccess(new DateTime(2017, 9, 12, 8, 35, 49, 340, DateTimeZone.UTC))
    }

    "not read from an invalid String" in {
      isoDateTimeReads.reads(JsString("notDate")) shouldBe JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.date.isoformat"))))
    }

    "not read from a non-String" in {
      isoDateTimeReads.reads(Json.obj()) shouldBe JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.date"))))
    }
  }

}
