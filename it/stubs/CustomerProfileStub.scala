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

package stubs

import stubs.StubShortcuts._

object CustomerProfileStub {

  def versionCheckSucceeds(upgrade: Boolean) : Unit = {
    val response =
      s"""
        |{"upgrade":$upgrade}
      """.stripMargin
    stubPostSuccess("/profile/native-app/version-check", response)
  }

  def versionCheckUpgradeRequiredFails(status: Int) : Unit = {
    val error =
      """
        |{"error":"Controlled Explosion"}
      """.stripMargin
    stubPostFailure("/profile/native-app/version-check", status, error)
  }

}
