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

import com.google.inject.AbstractModule
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

  override def configure() = {

    bind(classOf[SandboxOrchestrationController]).to(classOf[SandboxOrchestrationControllerImpl])
    bind(classOf[AuthConnector]).to(classOf[ConcreteAuthConnector])
    bind(classOf[AuditConnector]).toInstance(NextGenAuditConnector)

    bindConstant().annotatedWith(Names.named("serviceMax"))
      .to(configuration.getInt("supported.generic.service.max").getOrElse(1))

    bindConstant().annotatedWith(Names.named("eventMax"))
      .to(configuration.getInt("supported.generic.event.max").getOrElse(1))

    bindConstant().annotatedWith(Names.named("confidenceLevel"))
      .to(configuration.getInt("controllers.confidenceLevel").getOrElse(throw new Exception()))

    bindConstant().annotatedWith(Names.named("pollMaxAge"))
      .to(configuration.getInt("poll.success.maxAge").getOrElse(throw new Exception(s"Failed to resolve config key poll.success.maxAge")))

    bind(classOf[Configuration]).annotatedWith(Names.named("config")).toInstance(configuration)

  }
}
