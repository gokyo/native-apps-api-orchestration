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

import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import org.slf4j.Logger
import play.api.LoggerLike
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveStartupSpec extends UnitSpec with MockFactory with OneInstancePerTest {
  private val connectorStub = stub[GenericConnector]

  // when https://github.com/paulbutcher/ScalaMock/issues/39 is fixed we will be able to simplify this code by mocking LoggerLike directly (instead of slf4j.Logger)
  private val slf4jLoggerStub = stub[Logger]
  (slf4jLoggerStub.isWarnEnabled: () => Boolean).when().returning(true)
  private val logger = new LoggerLike {
    override val logger: Logger = slf4jLoggerStub
  }

  private val executor = HelpToSaveStartup(logger, connectorStub)
  private val serviceName = "mobile-help-to-save"

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "execute" should {
    "pass through JSON object returned by mobile-help-to-save startup endpoint inside id helpToSave" in {
      val jsonReturnedByHelpToSaveStartup = Json.obj("arbitraryParameter" -> true)

      (connectorStub.doGet(_: String, _: String, _: HeaderCarrier)(_: ExecutionContext)) when(serviceName, "/mobile-help-to-save/startup", hc, *) returns (Future successful jsonReturnedByHelpToSaveStartup)

      val executorResponse = await(executor.execute("AA000000A", 2017))
      executorResponse shouldBe Some(Result("helpToSave", jsonReturnedByHelpToSaveStartup))
    }

    "log the exception and return None when an exception occurs whilst calling mobile-help-to-save" in {
      val thrownException = new RuntimeException("Oh no!")

      (connectorStub.doGet(_: String, _: String, _: HeaderCarrier)(_: ExecutionContext)) when(serviceName, "/mobile-help-to-save/startup", hc, *) returns (Future failed thrownException)

      val executorResponse = await(executor.execute("AA000000A", 2017))
      executorResponse shouldBe None

      (slf4jLoggerStub.warn(_: String, _: Throwable)) verify("""Exception thrown by "/mobile-help-to-save/startup", not returning any helpToSave result""", thrownException)
    }
  }
}
