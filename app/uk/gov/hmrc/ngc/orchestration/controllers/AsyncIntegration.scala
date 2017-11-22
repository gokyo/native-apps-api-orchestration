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

package uk.gov.hmrc.ngc.orchestration.controllers

import akka.actor.{ActorRef, ActorSystem, Props}
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Call, Controller, Request}
import uk.gov.hmrc.play.asyncmvc.async.{AsyncMVC, AsyncPaths}
import play.api.Play.current
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

/**
  * All subclasses must be @Singletons because this trait registers a shutdown hook
  * https://www.playframework.com/documentation/2.5.x/ScalaDependencyInjection#stopping-cleaning-up
  */
trait AsyncMvcIntegration extends AsyncMVC[AsyncResponse] {

  self:Controller =>

  // TODO it would be better to inject these
  private def actorSystem: ActorSystem = Akka.system
  private def lifecycle: ApplicationLifecycle = current.injector.instanceOf[ApplicationLifecycle]

  val actorName = "async_native-apps-api-actor"
  override def id = "async_native-apps-api-id"

  override def asyncPaths(implicit request:Request[AnyContent]) = Seq(
    AsyncPaths(id, "/poll")
  )

  override def outputToString(in:AsyncResponse): String = {
    implicit val format = Json.format[AsyncResponse]
    Json.stringify(Json.toJson(in))
  }

  override def convertToJSONType(in:String) : AsyncResponse = {
    val json=Json.parse(in)
    val response=json.asOpt[AsyncResponse]
    response.getOrElse(throw new Exception("Failed to build the response object?"))
  }

  override def waitForAsync = Call("GET","/poll")

  override def      throttleLimit = 1000 // Max number of async tasks that can run on each server.
  override def  blockingDelayTime = 3000

  final val CLIENT_TIMEOUT=115000L

  lazy val asyncActor: ActorRef = {
    lifecycle.addStopHook { () =>
      Future.successful {
        Logger.debug(s"Stopping actor $actorName")
        actorSystem.stop(actorRef)
      }
    }

    actorSystem.actorOf(Props(new AsyncMVCAsyncActor(taskCache, CLIENT_TIMEOUT)), actorName)
  }

  override def         actorRef = asyncActor
  override def getClientTimeout = CLIENT_TIMEOUT

}