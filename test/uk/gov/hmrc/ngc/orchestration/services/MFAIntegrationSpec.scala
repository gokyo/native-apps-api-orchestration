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
import org.mockito.ArgumentMatchers.{any, anyString, eq ⇒ eqs}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.controllers.BadRequestException
import uk.gov.hmrc.ngc.orchestration.domain.Accounts
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MFAIntegrationSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  trait mocks {
    implicit val mockMFAIntegration: MFAIntegration = mock[MFAIntegration]
    implicit val mockGenericConnector: GenericConnector = mock[GenericConnector]
    implicit val mockAuditConnector: AuditConnector = mock[AuditConnector]
    implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val host = "localhost"
  val port = 33333
  val nino = "CS700100A"
  val randomUUID: String = UUID.randomUUID().toString
  val journeyId: String = randomUUID

  val mfaAuthenticatedJourney = "/multi-factor-authentication/authenticatedJourney"
  val mfaApiURI = "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"

  val scopes = Seq(
    "read:personal-income",
    "read:customer-profile",
    "read:messages",
    "read:submission-tracker",
    "read:web-session",
    "read:native-apps-api-orchestration")

  def stubHostAndPortGenericConnector()(implicit genericConnector: GenericConnector): Unit = {
    when(genericConnector.host(anyString())).thenReturn(host)
    when(genericConnector.port(anyString())).thenReturn(port)
  }

  def stubGETGenericConnectorResponse(path: String, response: JsValue)(implicit genericConnector: GenericConnector): Unit = {
    when(genericConnector.doGet(anyString(), eqs[String](path), any[HeaderCarrier]())(any[ExecutionContext]()))
      .thenReturn(Future(response))
  }

  def stubPOSTGenericConnectorResponse(path: String, response: JsValue)(implicit genericConnector: GenericConnector): Unit = {
    when(genericConnector.doPost[JsValue](any[JsValue](), anyString(), eqs[String](path), any[HeaderCarrier]())(any(), any(), any[ExecutionContext]()))
      .thenReturn(Future.successful(response))
  }

  def stubUpdateMainAuthorityCredentialStrengthStrong()(implicit genericConnector: GenericConnector): Unit = {
    when(genericConnector.doPostIgnoreResponse(eqs[JsValue](Json.obj()), eqs[String]("auth"), eqs[String]("/auth/credential-strength/strong"),any[HeaderCarrier]())(any[ExecutionContext]()))
      .thenReturn(Future.successful(()))
  }

  def stubPOSTGenericConnectorFailure(path: String, status: Int)(implicit genericConnector: GenericConnector): Any = {
    if(status >= 400 && status < 500) {
      when(genericConnector.doPost(any[JsValue](), anyString(), eqs[String](path), any[HeaderCarrier]())(any(), any(), any[ExecutionContext]()))
        .thenReturn(Future.failed(Upstream4xxResponse("", status, status)))
    }
    if(status == 500) {
      when(genericConnector.doPost(any[JsValue](), anyString(), eqs[String](path), any[HeaderCarrier]())(any(), any(), any[ExecutionContext]()))
        .thenReturn(Future.failed(Upstream5xxResponse("", status, status)))
    }
    if(status == 504) {
      when(genericConnector.doPost(any[JsValue](), anyString(), eqs[String](path), any[HeaderCarrier]())(any(), any(), any[ExecutionContext]()))
        .thenReturn(Future.failed(new GatewayTimeoutException("")))
    }
  }

  def stubBearerTokenExchange()(implicit genericConnector: GenericConnector) = {
    when(genericConnector.doPost[AuthExchangeResponse](any[JsValue], anyString(), eqs[String]("/auth/gg/some-cred-id/exchange"), any[HeaderCarrier]())(any(), any(), any[ExecutionContext]()))
      .thenReturn(Future.successful(new AuthExchangeResponse(access_token = new BearerToken(UUID.randomUUID().toString, DateTime.now().plusDays(1)), 10000)))
  }


  "MFAIntegration with start request" should {

    "return the MFA API URI and return routeToTwoFactor=true when accounts.routeToTwoFactor=true" in new mocks {
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
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, expectedResponse)
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = true, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("start", None)
      val response = await(mfaIntegration.mfaDecision(testAccount, Some(mfaRequest), Some(journeyId)))
      response.value.routeToTwoFactor shouldBe true
      response.value.mfa.value.webURI shouldBe "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
      response.value.mfa.value.apiURI shouldBe "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
    }

    "return None when accounts.routeToTwoFactor=false" in new mocks {
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
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, expectedResponse)
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = false, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("start", None)
      val response = await(mfaIntegration.mfaDecision(testAccount, Some(mfaRequest), Some(journeyId)))
      response shouldBe None
    }

    "return None when there is no MFARequest" in new mocks {
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
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, expectedResponse)
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = false, journeyId, "some-cred-id", "Individual")
      val response = await(mfaIntegration.mfaDecision(testAccount, mfa = None, Some(journeyId)))
      response shouldBe None
    }

    "return bad request when the MFA operation supplied is invalid" in new mocks {
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = true, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("INVALID_OPERATION", None)
      intercept[BadRequestException] {
        await(mfaIntegration.mfaDecision(testAccount, Some(mfaRequest), Some(journeyId)))
      }
    }

  }

  "MFAIntegration with outcome request" should {

    "return the MFA API URI and return routeToTwoFactor=true when when accounts.routeToTwoFactor=true and MFA API returns UNVERIFIED state" in new mocks {

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
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      stubGETGenericConnectorResponse(mfaApiURI, expectedResponse)
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = true, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", Some("/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"))
      val response = await(mfaIntegration.mfaDecision(testAccount, Some(mfaRequest), Some(journeyId)))
      response.value.routeToTwoFactor shouldBe true
      response.value.mfa.value.webURI shouldBe "http://localhost:9721/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
      response.value.mfa.value.apiURI shouldBe "/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"
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
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = true, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", None)
      intercept[BadRequestException] {
        await(mfaIntegration.mfaDecision(testAccount, Some(mfaRequest), Some(journeyId)))
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
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      stubGETGenericConnectorResponse(mfaApiURI, expectedResponse)
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = true, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", Some("/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"))
      val response = await(mfaIntegration.mfaDecision(testAccount, Some(mfaRequest), Some(journeyId)))
      response.value.routeToTwoFactor shouldBe false
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
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      stubGETGenericConnectorResponse(mfaApiURI, expectedResponse)
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = true, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", Some("/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"))
      val response = await(mfaIntegration.mfaDecision(testAccount, Some(mfaRequest), Some(journeyId)))
      response.value.routeToTwoFactor shouldBe false
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
      stubHostAndPortGenericConnector()
      stubPOSTGenericConnectorResponse(mfaAuthenticatedJourney, mfaAuthenticatedJourneyResponse)
      stubGETGenericConnectorResponse(mfaApiURI, expectedResponse)
      stubBearerTokenExchange()
      stubUpdateMainAuthorityCredentialStrengthStrong()
      val mfaIntegration = new MFAIntegration(mockGenericConnector, scopes)
      val testAccount = Accounts(Some(Nino(nino)), None, routeToIV = false, routeToTwoFactor = true, journeyId, "some-cred-id", "Individual")
      val mfaRequest = MFARequest("outcome", Some("/multi-factor-authentication/journey/58d93f54280000da005d388b?origin=NGC"))
      val response = await(mfaIntegration.mfaDecision(testAccount, Some(mfaRequest), Some(journeyId)))
      response.value.routeToTwoFactor shouldBe false
    }
  }

  // remove implicit
  override def liftFuture[A](v: A): Future[A] = super.liftFuture(v)
}