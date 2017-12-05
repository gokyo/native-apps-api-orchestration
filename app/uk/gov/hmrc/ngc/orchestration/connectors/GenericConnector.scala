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

package uk.gov.hmrc.ngc.orchestration.connectors

import javax.inject.Inject

import com.google.inject.Singleton
import play.api.libs.json.{JsValue, Writes}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.http._
import uk.gov.hmrc.ngc.orchestration.config.WSHttp

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GenericConnector @Inject() (configuration: Configuration) {

  def host(serviceName: String) = getConfigProperty(serviceName, "host")

  def port(serviceName: String) = getConfigProperty(serviceName, "port").toInt

  def http: CorePost with CoreGet = WSHttp

  def logHC(hc: HeaderCarrier, path:String) =   Logger.info(s"transport: HC received is ${hc.authorization} for path $path")

  def doGet(serviceName:String, path:String, hc: HeaderCarrier)(implicit ec: ExecutionContext): Future[JsValue] = {
    implicit val hcHeaders = addAPIHeaders(hc)
    logHC(hc, s"transport: HC received is ${hc.authorization} for path $path")
    http.GET[JsValue](buildUrl(host(serviceName), port(serviceName), path))
  }

  def doPost[T](json:JsValue, serviceName:String, path:String, hc: HeaderCarrier)(implicit wts: Writes[T], rds: HttpReads[T], ec: ExecutionContext): Future[T] = {
    implicit val hcHeaders = addAPIHeaders(hc)
    logHC(hc, s"transport: HC received is ${hc.authorization} for path $path")
    http.POST[JsValue, T](buildUrl(host(serviceName), port(serviceName), path), json)
  }

  def doGetRaw(serviceName:String, path:String, hc: HeaderCarrier)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    implicit val hcHeaders = addAPIHeaders(hc)
    http.GET(buildUrl(host(serviceName), port(serviceName), path))
  }

  private def addAPIHeaders(hc:HeaderCarrier) = hc.withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")
  private def buildUrl(host:String, port:Int, path:String) =  s"""http://$host:$port$path"""
  private def getConfigProperty(serviceName: String, property: String): String = {
    getServiceConfig(serviceName).getString(property)
      .getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }
  private def getServiceConfig(serviceName: String): Configuration = {
    configuration.getConfig(s"microservice.services.$serviceName")
      .getOrElse(throw new Exception(s"No micro services configured for $serviceName"))
  }
}
