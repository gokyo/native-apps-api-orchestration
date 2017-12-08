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

import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.{HttpGet, HttpPost}
import uk.gov.hmrc.play.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost}

@Singleton
class WSHttp @Inject() (override val runModeConfiguration: Configuration, environment: Environment) extends HttpGet with HttpPost with WSGet with WSPost with RunMode {
  override val hooks = NoneRequired
  override protected def mode = environment.mode
}

@Singleton
class MicroserviceAuthConnector @Inject()(override val runModeConfiguration: Configuration, environment: Environment, wsHttp: WSHttp) extends PlayAuthConnector with ServicesConfig {
  override lazy val serviceUrl = baseUrl("auth")
  override def http = wsHttp
  override protected def mode = environment.mode
}


