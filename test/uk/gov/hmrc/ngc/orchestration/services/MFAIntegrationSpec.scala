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

package uk.gov.hmrc.ngc.orchestration.services

import java.util.UUID

import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.controllers.BadRequestException
import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, MfaURI}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class MFAIntegrationSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  trait mocks {
    implicit val mockMFAIntegration: MFAIntegration = mock[MFAIntegration]
    implicit val mockGenericConnector: GenericConnector = mock[GenericConnector]
    implicit val mockAuditConnector: AuditConnector = mock[AuditConnector]
    implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockGenericConnector: GenericConnector = mock[GenericConnector]
  val mockConfiguration: Configuration = mock[Configuration]
  val host = "localhost"
  val port = 33333
  val nino = "CS700100A"
  val randomUUID: String = UUID.randomUUID().toString
  val journeyId: String = randomUUID

  val mfaAuthenticatedJourney = "/multi-factor-authentication/authenticatedJourney"
  val mfaApiURI = "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"

  def stubConfiguration(): OngoingStubbing[Option[Seq[String]]] = Mockito.when(mockConfiguration.getStringSeq(ArgumentMatchers.eq("scopes")))
    .thenReturn(Some(Seq( "read:personal-income",
                          "read:customer-profile",
                          "read:messages",
                          "read:submission-tracker",
                          "read:web-session",
                          "read:native-apps-api-orchestration")))

  def stubHostAndPortGenericConnector()(implicit genericConnector: GenericConnector): OngoingStubbing[Int] = {
    when(genericConnector.host(ArgumentMatchers.anyString())).thenReturn(host)
    when(genericConnector.port(ArgumentMatchers.anyString())).thenReturn(port)
  }

  def stubGETGenericConnectorResponse(path: String, response: JsValue)(implicit genericConnector: GenericConnector): OngoingStubbing[Future[JsValue]] = {
    when(genericConnector.doGet(ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future(response))
  }

  def stubPOSTGenericConnectorResponse(path: String, response: JsValue)(implicit genericConnector: GenericConnector): OngoingStubbing[Future[JsValue]] = {
    when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
      .thenReturn(Future.successful(response))
  }

  def stubPOSTGenericConnectorFailure(path: String, status: Int)(implicit genericConnector: GenericConnector): Any = {
    if(status >= 400 && status < 500) {
      when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(Upstream4xxResponse("", status, status)))
    }
    if(status == 500){
      when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(Upstream5xxResponse("", status, status)))
    }
    if(status == 504){
      when(genericConnector.doPost(ArgumentMatchers.any[JsValue](), ArgumentMatchers.anyString(), ArgumentMatchers.eq[String](path), ArgumentMatchers.any[HeaderCarrier]())(ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.failed(new GatewayTimeoutException("")))
    }
  }


  "MFAIntegration with start request" should {

    "call the MFA API URI and return routeToTwoFactor=true when cred-strength is not strong" in new mocks {
      val expectedResponse = Json.parse("""
                               |{
                               |  "_links": {
                               |    "browser": {
                               |      "href": "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                               |    },
                               |    "self": {
                               |      "href": "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                               |    }
                               |  }
                               |}
                             """.stripMargin)
      stubConfiguration()
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, expectedResponse)
      val mFAIntegration = new MFAIntegration(mockGenericConnector, mockConfiguration)
      val testAccount = Accounts(Some(Nino(nino)), None, false, false, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("start", None)
      val response = await(mFAIntegration.verifyMFAStatus(mfaRequest, testAccount, Some(journeyId)))
      response.routeToTwoFactor shouldBe true

    }

    "return bad request when the MFA operation supplied is invalid" in new mocks {
      stubConfiguration()
      val mFAIntegration = new MFAIntegration(mockGenericConnector, mockConfiguration)
      val testAccount = Accounts(Some(Nino(nino)), None, false, false, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("INVALID_OPERATION", None)
      intercept[BadRequestException] {
        await(mFAIntegration.verifyMFAStatus(mfaRequest, testAccount, Some(journeyId)))
      }
    }

  }

  "MFAIntegration with outcome request" should {

    "call the MFA API URI and return routeToTwoFactor=true when MFA API returns UNVERIFIED state" in new mocks {

      val mfaAuthenticatedJourneyResponse = Json.parse("""
                                                         |{
                                                         |  "_links": {
                                                         |    "browser": {
                                                         |      "href": "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    },
                                                         |    "self": {
                                                         |      "href": "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    }
                                                         |  }
                                                         |}
                                                       """.stripMargin)
      val expectedResponse = Json.toJson(JourneyResponse("","",None,"","","",false,None,None,"UNVERIFIED",DateTime.now))
      stubConfiguration()
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      stubGETGenericConnectorResponse(mfaApiURI, expectedResponse)
      val mFAIntegration = new MFAIntegration(mockGenericConnector, mockConfiguration)
      val testAccount = Accounts(Some(Nino(nino)), None, false, false, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", Some("/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"))
      val response = await(mFAIntegration.verifyMFAStatus(mfaRequest, testAccount, Some(journeyId)))
      response.routeToTwoFactor shouldBe true
    }

    "return bad request when the apiURI is not included in the request" in new mocks {

      val mfaAuthenticatedJourneyResponse = Json.parse("""
                                                         |{
                                                         |  "_links": {
                                                         |    "browser": {
                                                         |      "href": "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    },
                                                         |    "self": {
                                                         |      "href": "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    }
                                                         |  }
                                                         |}
                                                       """.stripMargin)
      stubConfiguration()
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      val mFAIntegration = new MFAIntegration(mockGenericConnector, mockConfiguration)
      val testAccount = Accounts(Some(Nino(nino)), None, false, false, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", None)
      intercept[BadRequestException] {
        await(mFAIntegration.verifyMFAStatus(mfaRequest, testAccount, Some(journeyId)))
      }

    }

    "return response with routeToTwoFactor=false when MFA returns NOT_REQUIRED state" in new mocks {
      val mfaAuthenticatedJourneyResponse = Json.parse("""
                                                         |{
                                                         |  "_links": {
                                                         |    "browser": {
                                                         |      "href": "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    },
                                                         |    "self": {
                                                         |      "href": "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    }
                                                         |  }
                                                         |}
                                                       """.stripMargin)
      val expectedResponse = Json.toJson(JourneyResponse("","",None,"","","",false,None,None,"NOT_REQUIRED",DateTime.now))
      stubConfiguration()
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      stubGETGenericConnectorResponse(mfaApiURI, expectedResponse)
      val mFAIntegration = new MFAIntegration(mockGenericConnector, mockConfiguration)
      val testAccount = Accounts(Some(Nino(nino)), None, false, false, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", Some("/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"))
      val response = await(mFAIntegration.verifyMFAStatus(mfaRequest, testAccount, Some(journeyId)))
      response.routeToTwoFactor shouldBe false
    }

    "return response with routeToTwoFactor=false when MFA returns SKIPPED state" in new mocks {
      val mfaAuthenticatedJourneyResponse = Json.parse("""
                                                         |{
                                                         |  "_links": {
                                                         |    "browser": {
                                                         |      "href": "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    },
                                                         |    "self": {
                                                         |      "href": "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    }
                                                         |  }
                                                         |}
                                                       """.stripMargin)
      val expectedResponse = Json.toJson(JourneyResponse("","",None,"","","",false,None,None,"SKIPPED",DateTime.now))
      stubConfiguration()
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      stubGETGenericConnectorResponse(mfaApiURI, expectedResponse)
      val mFAIntegration = new MFAIntegration(mockGenericConnector, mockConfiguration)
      val testAccount = Accounts(Some(Nino(nino)), None, false, false, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", Some("/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"))
      val response = await(mFAIntegration.verifyMFAStatus(mfaRequest, testAccount, Some(journeyId)))
      response.routeToTwoFactor shouldBe false
    }

    "return response with routeToTwoFactor set to false when MFA returns VERIFIED state" in new mocks{

      val mfaAuthenticatedJourneyResponse = Json.parse("""
                                                         |{
                                                         |  "_links": {
                                                         |    "browser": {
                                                         |      "href": "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    },
                                                         |    "self": {
                                                         |      "href": "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
                                                         |    }
                                                         |  }
                                                         |}
                                                       """.stripMargin)
      val expectedResponse = Json.toJson(JourneyResponse("","",None,"","","",false,None,None,"VERIFIED",DateTime.now))
      stubConfiguration()
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      stubGETGenericConnectorResponse(mfaApiURI, expectedResponse)
      val mFAIntegration = new MFAIntegration(mockGenericConnector, mockConfiguration)
      val testAccount = Accounts(Some(Nino(nino)), None, false, false, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", Some("/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"))
      val response = await(mFAIntegration.verifyMFAStatus(mfaRequest, testAccount, Some(journeyId)))
      response.routeToTwoFactor shouldBe false

    }

  }
}