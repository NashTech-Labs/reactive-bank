package com.reactivebank.accounts

import akka.actor.Status
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ContentTypes, MessageEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import com.reactivebank.accounts.AccountActor.AccountNotFoundException
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec

class AccountRoutesTest
  extends AnyWordSpec
    with ScalatestRouteTest
    with AccountHelpers
    with AccountJsonFormats
    with ScalaFutures {

  val accounts = TestProbe()
  val http = new AccountRoutes(accounts.ref)

  "POST to /account" should {
    "create a new account" in {
      val account = generateAccount()
      val request = AccountActor.OpenAccount(account.accountHolder)

      val result = Post("/account")
        .withEntity(Marshal(request).to[MessageEntity].futureValue) ~>
        http.routes ~>
        runRoute

      accounts.expectMsgPF() {
        case AccountActor.Envelope(_, AccountActor.OpenAccount(accountHolder)) =>
          assert(accountHolder === account.accountHolder)
        case msg => fail("Unexpected Message: "+msg)
      }

      accounts.reply(AccountActor.AccountOpened(account))

      check {
        assert(status === StatusCodes.OK)
        assert(contentType === ContentTypes.`application/json`)
        assert(entityAs[Account] === account)
      } (result)
    }
  }

  "GET to /account/{id}" should {
    "return the account" in {
      val account = generateAccount()
      val result = Get(s"/account/${account.id.value.toString}") ~>
        http.routes ~>
        runRoute

      accounts.expectMsg(AccountActor.Envelope(account.id, AccountActor.GetAccount()))
      accounts.reply(account)

      check {
        assert(status === StatusCodes.OK)
        assert(contentType === ContentTypes.`application/json`)
        assert(entityAs[Account] === account)
      }(result)
    }
    "return a meaningful error if the account doesn't exist" in {
      val accountNumber = generateAccountNumber()
      val result = Get(s"/account/${accountNumber.value.toString}") ~>
        http.routes ~>
        runRoute

      val expectedError = AccountActor.AccountNotFoundException(accountNumber)

      accounts.expectMsg(AccountActor.Envelope(accountNumber, AccountActor.GetAccount()))
      accounts.reply(Status.Failure(expectedError))

      check {
        assert(status === StatusCodes.NotFound)
        assert(contentType === ContentTypes.`text/plain(UTF-8)`)
        assert(entityAs[String] === expectedError.getMessage)
      }(result)
    }
  }

  "POST to /account/{id}/amount" should {
    "credit balance to the account" in {
      val creditAmount = generateCreditAmount()
      val account = generateAccount().addBalance(creditAmount)
      val request = AccountActor.CreditAmountToAccount(creditAmount)

      val result = Post(s"/account/${account.id.value.toString}/amount")
        .withEntity(Marshal(request).to[MessageEntity].futureValue) ~>
        http.routes ~>
        runRoute

      accounts.expectMsg(AccountActor.Envelope(account.id, request))
      accounts.reply(AccountActor.AmountCreditedToAccount(account))

      check {
        assert(status === StatusCodes.OK)
        assert(contentType === ContentTypes.`application/json`)
        assert(entityAs[Account] === account)
      }(result)
    }
    "return a meaningful error if the account doesn't exist" in {
      val accountNumber = generateAccountNumber()
      val creditAmount = generateCreditAmount()
      val request = AccountActor.CreditAmountToAccount(creditAmount)

      val expectedError = AccountNotFoundException(accountNumber)

      val result = Post(s"/account/${accountNumber.value.toString}/amount")
        .withEntity(Marshal(request).to[MessageEntity].futureValue) ~>
        http.routes ~>
        runRoute

      accounts.expectMsg(AccountActor.Envelope(accountNumber, request))
      accounts.reply(Status.Failure(expectedError))

      check {
        assert(status === StatusCodes.NotFound)
        assert(contentType === ContentTypes.`text/plain(UTF-8)`)
        assert(entityAs[String] === expectedError.getMessage)
      }(result)    }
  }

}
