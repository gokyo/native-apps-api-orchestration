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

package uk.gov.hmrc.ngc.orchestration.config

import com.google.inject.{AbstractModule, TypeLiteral}
import com.google.inject.name.Names
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.ngc.orchestration.controllers.{SandboxOrchestrationController, SandboxOrchestrationControllerImpl}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.ServicesConfig

class GuiceModule(environment: Environment, configuration: Configuration) extends AbstractModule with ServicesConfig {

  override protected lazy val mode: Mode = environment.mode
  override protected lazy val runModeConfiguration: Configuration = configuration

  override def configure(): Unit = {

    bind(classOf[SandboxOrchestrationController]).to(classOf[SandboxOrchestrationControllerImpl])

    bind(classOf[AuthConnector]).to(classOf[MicroserviceAuthConnector])

    bind(classOf[AuditConnector]).toInstance(MicroserviceAuditConnector)

    bindConfigInt("supported.generic.service.max")
    bindConfigInt("supported.generic.event.max")

    bindConfigInt("controllers.confidenceLevel")

    bindConfigInt("poll.success.maxAge")

    bindConfigStringSeq("scopes")
  }

  /**
    * Binds a configuration value using the `path` as the name for the binding.
    * Throws an exception if the configuration value does not exist or cannot be read as an Int.
    */
  private def bindConfigInt(path: String): Unit = {
    bindConstant().annotatedWith(Names.named(path))
      .to(configuration.underlying.getInt(path))
  }

  private def bindConfigStringSeq(path: String): Unit = {
    val configValue: Seq[String] = configuration.getStringSeq(path).getOrElse(throw new RuntimeException(s"""Config property "$path" missing"""))
    bind[Seq[String]](new TypeLiteral[Seq[String]] {})
      .annotatedWith(Names.named(path))
      .toInstance(configValue)
  }
}
