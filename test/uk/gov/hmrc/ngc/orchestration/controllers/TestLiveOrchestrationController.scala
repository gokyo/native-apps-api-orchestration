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

import javax.inject.Provider

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.mongo.{DatabaseUpdate, Updated}
import uk.gov.hmrc.msasync.repository.{AsyncRepository, TaskCachePersist}
import uk.gov.hmrc.ngc.orchestration.services.LiveOrchestrationService
import uk.gov.hmrc.play.asyncmvc.model.TaskCache
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.Future

class TestProvider[T](obj: T) extends Provider[T] {
  override def get(): T = obj
}

class TestLiveOrchestrationController(appNameConfiguration: Configuration, auditConnector: AuditConnector, authConnector: AuthConnector, service: LiveOrchestrationService, actorSystem: ActorSystem, lifecycle: ApplicationLifecycle, reactiveMongo: ReactiveMongoComponent, serviceMax: Int, eventMax:Int, confLevel: Int , maxAgeForSuccess: Int, override val actorName: String) extends
  LiveOrchestrationController(appNameConfiguration: Configuration, auditConnector: AuditConnector, authConnector: AuthConnector, service: LiveOrchestrationService, actorSystem, lifecycle, new TestProvider[ReactiveMongoComponent](reactiveMongo), serviceMax: Int, eventMax:Int, confLevel: Int , maxAgeForSuccess: Int) {

  override lazy val repository = new AsyncRepository {

    var cache:Option[Map[BSONObjectID, TaskCache]] = Some(Map())

    override def findByTaskId(id: String): Future[Option[TaskCachePersist]] = {
      val update = TaskCachePersist(BSONObjectID.apply(id), cache.get(BSONObjectID.apply(id)))
      Future.successful(Some(update))
    }

    override def removeById(id: String): Future[Unit] = Future.successful({})

    override def save(expectation: TaskCache, expire: Long): Future[DatabaseUpdate[TaskCachePersist]] = {
      val id = BSONObjectID.generate
      cache++ Some(Map(id → expectation))
      val update = TaskCachePersist(id, expectation)
      Future.successful(DatabaseUpdate(null, Updated(update,update)))
    }
  }
}
