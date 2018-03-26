/*
 * Copyright 2018 HM Revenue & Customs
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

import java.util.UUID.randomUUID

import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.ConfidenceLevel.{L100, L200, L50}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.ngc.orchestration.controllers.{AccountWithLowCL, FailToMatchTaxIdOnAuth, NinoNotFoundOnAccount}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AuthorisationSpec extends UnitSpec with MockFactory with OneInstancePerTest {
  implicit val hc = HeaderCarrier()

  val journeyId: String = randomUUID().toString
  val testNino: Nino = Nino("CS700100A")
  val testSaUtr: SaUtr = SaUtr("1872796160")
  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  type AccountsRetrieval = Retrieval[Option[String] ~ Option[String] ~ Option[AffinityGroup] ~ Credentials ~ ConfidenceLevel]

  type GrantAccessRetrieval = Retrieval[Option[String] ~ ConfidenceLevel]

  val accountsRetrievals = nino and saUtr and affinityGroup and credentials and confidenceLevel

  val grantAccessRetrievals = nino and confidenceLevel

  val userCredentials = Credentials("some-cred-id", "GovernmentGateway")

  def authPredicate(nino: Nino): Predicate = {
    Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino.nino)), "Activated", None)
  }

  def authorisation(mockAuthConnector: AuthConnector): Authorisation = {
    new Authorisation {
      override val confLevel: Int = 200
      override def authConnector: AuthConnector = mockAuthConnector
    }
  }

  def authoriseWillAllowAccessForEmptyPredicate(
    authConnector: AuthConnector, nino: Option[Nino], saUtr: SaUtr, affinityGroup: Option[AffinityGroup], credentials: Credentials, confLevel: ConfidenceLevel) = {
    val returningNino = if (nino.isDefined) Some(nino.get.nino) else None
    (authConnector.authorise(_: Predicate, _: AccountsRetrieval)(_: HeaderCarrier, _: ExecutionContext))
      .expects(EmptyPredicate, accountsRetrievals, *, *)
      .returning(Future.successful(returningNino and Option(saUtr.value) and affinityGroup and credentials and confLevel))
  }

  def authoriseWillAllowAccessForNinoPredicates(nino: Nino, confLevel: ConfidenceLevel, returnNino: Option[String]) = {
    (mockAuthConnector.authorise(_: Predicate, _: GrantAccessRetrieval)(_: HeaderCarrier, _: ExecutionContext))
      .expects(authPredicate(nino), grantAccessRetrievals, *, *)
      .returning(Future.successful(returnNino and confLevel))
  }

  "Authorisation getAccounts" should {

    "find the user and routeToIV should be false when confidence is L200" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(Individual), userCredentials, L200)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(journeyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId shouldBe journeyId
    }

    "ignore the supplied journeyId when blank" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(Individual), userCredentials, L200)
      val emptyJourneyId = ""
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(emptyJourneyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId should not be emptyJourneyId
      accounts.journeyId.length should not equal 0
    }

    "generates new journeyId when no journeyId supplied" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(Individual), userCredentials, L200)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(None))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId should not be None
      accounts.journeyId.length should not equal 0
    }

    "throws an UnsupportedAuthProvider AuthorisationException if the AuthProvider is not GovernmentGateway" in {
      intercept[UnsupportedAuthProvider] {
        authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(Individual), Credentials("some-cred-id", "Not_Government_Gateway"),  L200)
        await(authorisation(mockAuthConnector).getAccounts(None))
      }
    }
  }

  "Authorisation grantAccess" should {

    "successfully grant access when nino exists and confidence level is 200" in {
      authoriseWillAllowAccessForNinoPredicates(testNino, L200, Some(testNino.nino))
      val authority = await(authorisation(mockAuthConnector).grantAccess(testNino))
      authority.nino.value shouldBe testNino.nino
    }

    "error with unauthorised when account has low CL" in {
      authoriseWillAllowAccessForNinoPredicates(testNino, L100, Some(testNino.nino))
      intercept[AccountWithLowCL] {
        await(authorisation(mockAuthConnector).grantAccess(testNino))
      }
    }

    "fail to return authority when no NINO exists" in {
      authoriseWillAllowAccessForNinoPredicates(testNino, L200, None)
      intercept[NinoNotFoundOnAccount] {
        await(authorisation(mockAuthConnector).grantAccess(testNino))
      }

      authoriseWillAllowAccessForNinoPredicates(testNino, L200, Some(""))
      intercept[NinoNotFoundOnAccount] {
        await(authorisation(mockAuthConnector).grantAccess(testNino))
      }
    }

    "fail to return authority when auth NINO does not match request NINO" in {
      authoriseWillAllowAccessForNinoPredicates(testNino, L200, Some("AB123450C"))
      intercept[FailToMatchTaxIdOnAuth] {
        await(authorisation(mockAuthConnector).grantAccess(testNino))
      }
    }
  }

  "Authorisation getAccounts" should {
    "find the user with with L50 confidence level returning routeToIV true" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(Individual), userCredentials, L50)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(journeyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe true
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId shouldBe journeyId
    }

    "find a user with ith L200 confidence level returning routeToIV false" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, Some(testNino), testSaUtr, Some(Individual), userCredentials, L200)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(journeyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.nino.get.nino shouldBe testNino.nino
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId shouldBe journeyId
    }

    "find a user with an account with no nino" in {
      authoriseWillAllowAccessForEmptyPredicate(mockAuthConnector, None, testSaUtr, Some(Individual), userCredentials, L200)
      val accounts = await(authorisation(mockAuthConnector).getAccounts(Some(journeyId)))
      accounts.credId shouldBe "some-cred-id"
      accounts.affinityGroup shouldBe "Individual"
      accounts.routeToIV shouldBe false
      accounts.nino shouldBe None
      accounts.saUtr.get.value shouldBe testSaUtr.value
      accounts.journeyId shouldBe journeyId
    }
  }
}
