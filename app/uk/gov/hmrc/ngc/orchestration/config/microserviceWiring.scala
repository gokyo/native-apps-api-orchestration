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

import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.{HttpGet, HttpPost}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.microservice.connectors.AuthConnector
import uk.gov.hmrc.play.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost}
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig


trait WSHttp extends HttpGet with HttpPost with WSGet with WSPost with RunMode {
  override val hooks = NoneRequired
}

object WSHttp extends WSHttp

object MicroserviceAuditConnector extends AuditConnector with RunMode {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}

object MicroserviceAuthConnector extends AuthConnector with ServicesConfig with WSHttp {
  override val authBaseUrl = baseUrl("auth")
}

class ConcreteAuthConnector(val serviceUrl: String, val http: HttpPost) extends PlayAuthConnector


