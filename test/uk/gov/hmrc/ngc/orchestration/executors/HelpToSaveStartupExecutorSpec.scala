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

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OptionValues, WordSpec}
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveStartupExecutorSpec extends WordSpec with Matchers with MockFactory with FutureAwaits with DefaultAwaitTimeout with OptionValues {

  private val emptyConfiguration = new Configuration(ConfigFactory.empty())
  private val serviceName = "mobile-help-to-save"

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "execute" should {
    "pass through JSON object returned by mobile-help-to-save startup endpoint" in {
      val connectorStub = stub[GenericConnector]
      val executor = new HelpToSaveStartupExecutor(connectorStub, emptyConfiguration)

      val jsonReturnedByHelpToSaveStartup = Json.obj("arbitraryParameter" -> true)

      (connectorStub.doGet(_: String, _: String, _: HeaderCarrier)(_: ExecutionContext)) when(serviceName, "/mobile-help-to-save/startup", hc, *) returns (Future successful jsonReturnedByHelpToSaveStartup)

      val response = await(executor.execute(None, None, "AA000000A", None)).value
      response.responseData.value shouldBe jsonReturnedByHelpToSaveStartup
    }
  }

}
