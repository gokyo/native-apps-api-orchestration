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

import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, _}
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.auth.core.{AuthConnector, _}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.controllers.{AccountWithLowCL, FailToMatchTaxIdOnAuth, NinoNotFoundOnAccount}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AuthorisationSpec extends UnitSpec with MockFactory with OneInstancePerTest {


  implicit val hc = HeaderCarrier()

  val journeyId: String = UUID.randomUUID().toString
  val testNino: Nino = Nino("CS700100A")
  val testSaUtr: SaUtr = SaUtr("1872796160")
  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  type AccountsRetrieval = Retrieval[Option[String] ~ Option[String] ~ Option[AffinityGroup] ~ Credentials ~ Option[String] ~ ConfidenceLevel]

  type GrantAccessRetrieval = Retrieval[Option[String] ~ ConfidenceLevel]

  val accountsRetrievals = nino and saUtr and affinityGroup and credentials and credentialStrength and confidenceLevel

  val grantAccessRetrievals = nino and confidenceLevel

  def authorisation(mockAuthConnector: AuthConnector): Authorisation = {
    new Authorisation {
      override val confLevel: Int = 200
      override def authConnector: AuthConnector = mockAuthConnector
    }
  }

  def authoriseWillAllowAccessForEmptyPredicate(authConnector: AuthConnector, nino: Option[Nino], saUtr: SaUtr, affinityGroup: Option[AffinityGroup],
                                                credentials: Credentials, credStrength: Option[String],
                          confLevel: ConfidenceLevel) = {
    val returningNino = if (nino.isDefined) Some(nino.get.nino) else None
    (authConnector.authorise(_: Predicate, _: AccountsRetrieval)(_: HeaderCarrier, _: ExecutionContext))
      .expects(EmptyPredicate, accountsRetrievals, *, *)
      .returning(Future.successful(returningNino and Option(saUtr.value) and affinityGroup and credentials and credStrength and confLevel))
  }

  def authoriseWillAllowAccessForNinoAndCredStrengthStrongPredicates(nino: Nino, confLevel: ConfidenceLevel, returnNino: Option[String]) = {
    (mockAuthConnector.authorise(_: Predicate, _: GrantAccessRetrieval)(_: HeaderCarrier, _: ExecutionContext))
      .expects(Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino.nino)), "Activated", None) and CredentialStrength(CredentialStrength.strong), grantAccessRetrievals, *, *)
      .returning(Future.successful(returnNino and confLevel))
  }

  "Authorisation getAccounts" should {
    "find the user and routeToIV and routeToTwoFactor should be true" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(AffinityGroup.Individual), Credentials("some-cred-id", "GovernmentGateway"), Some("weak"), ConfidenceLevel.L50)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(journeyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe true
      accounts.routeToTwoFactor shouldBe true
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId shouldBe journeyId
    }

    "find the user and routeToIV is false when L200 confidence level and routeToTwoFactor is true" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(AffinityGroup.Individual), Credentials("some-cred-id", "GovernmentGateway"), Some("weak"), ConfidenceLevel.L200)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(journeyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.routeToTwoFactor shouldBe true
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId shouldBe journeyId
    }

    "find the user and routeToIV and routeToTwoFactor should be false when credential strength is 'string' and confidence is L200" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(AffinityGroup.Individual), Credentials("some-cred-id", "GovernmentGateway"), Some("strong"), ConfidenceLevel.L200)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(journeyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.routeToTwoFactor shouldBe false
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId shouldBe journeyId
    }

    "find the user with an account with no nino" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, None, testSaUtr, Some(AffinityGroup.Individual), Credentials("some-cred-id", "GovernmentGateway"), Some("weak"), ConfidenceLevel.L200)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(journeyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.routeToTwoFactor shouldBe true
      accounts.nino shouldBe None
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId shouldBe journeyId
    }

    "ignore the supplied journeyId when blank" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(AffinityGroup.Individual), Credentials("some-cred-id", "GovernmentGateway"), Some("strong"), ConfidenceLevel.L200)
      val emptyJourneyId = ""
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(emptyJourneyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.routeToTwoFactor shouldBe false
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId should not be emptyJourneyId
      accounts.journeyId.length should not equal 0
    }

    "generates new journeyId when no journeyId supplied" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(AffinityGroup.Individual), Credentials("some-cred-id", "GovernmentGateway"), Some("strong"), ConfidenceLevel.L200)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(None))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.routeToTwoFactor shouldBe false
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId should not be None
      accounts.journeyId.length should not equal 0
    }

    "throws an UnsupportedAuthProvider AuthorisationException if the AuthProvider is not GovernmentGateway" in {
      intercept[UnsupportedAuthProvider] {
        authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(AffinityGroup.Individual), Credentials("some-cred-id", "Not_Government_Gateway"), Some("strong"), ConfidenceLevel.L200)
        await(authorisation(mockAuthConnector).getAccounts(None))
      }
    }
  }

  "Authorisation grantAccess" should {

    "successfully grant access when nino exists and confidence level is 200 and credential strength is strong" in {
      authoriseWillAllowAccessForNinoAndCredStrengthStrongPredicates(testNino, ConfidenceLevel.L200, Some(testNino.nino))
      val authority = await(authorisation(mockAuthConnector).grantAccess(testNino))
      authority.nino.value shouldBe testNino.nino
    }

    "error with unauthorised when account has low CL" in {
      authoriseWillAllowAccessForNinoAndCredStrengthStrongPredicates(testNino, ConfidenceLevel.L100, Some(testNino.nino))
      intercept[AccountWithLowCL] {
        await(authorisation(mockAuthConnector).grantAccess(testNino))
      }
    }

    "fail to return authority when no NINO exists" in {
      authoriseWillAllowAccessForNinoAndCredStrengthStrongPredicates(testNino, ConfidenceLevel.L200, None)
      intercept[NinoNotFoundOnAccount] {
        await(authorisation(mockAuthConnector).grantAccess(testNino))
      }

      authoriseWillAllowAccessForNinoAndCredStrengthStrongPredicates(testNino, ConfidenceLevel.L200, Some(""))
      intercept[NinoNotFoundOnAccount] {
        await(authorisation(mockAuthConnector).grantAccess(testNino))
      }
    }

    "fail to return authority when auth NINO does not match request NINO" in {
      authoriseWillAllowAccessForNinoAndCredStrengthStrongPredicates(testNino, ConfidenceLevel.L200, Some("AB123450C"))
      intercept[FailToMatchTaxIdOnAuth] {
        await(authorisation(mockAuthConnector).grantAccess(testNino))
      }
    }
  }
}
