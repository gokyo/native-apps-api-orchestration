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

import com.github.tomakehurst.wiremock.client.WireMock._

object StubShortcuts {

  def stubGetSuccess(path: String, response: String) : Unit = {
    stubFor(get(urlPathEqualTo(path))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(response)))
  }

  def stubPostSuccess(path: String, response: String) : Unit = {
    stubFor(post(urlPathEqualTo(path))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(response)))
  }

  def stubPostFailure(path: String, status: Int,response: String) : Unit = {
    stubFor(post(urlPathEqualTo(path))
      .willReturn(aResponse()
        .withStatus(status)
        .withBody(response)))
  }

}
