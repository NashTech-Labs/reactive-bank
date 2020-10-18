package com.reactivebank.accounts

import akka.actor.{Actor, Props}
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.wordspec.AnyWordSpec
import spray.json.JsonParser

class JsonContractTest extends AnyWordSpec with ScalatestRouteTest with AccountHelpers {
  class AccountSupervisor(repository: AccountRepository) extends Actor {
    private def createAccountActor(accountNumber: AccountNumber) = {
      context.actorOf(AccountActor.props(repository), accountNumber.value.toString)
    }

    override def receive = {
      case AccountActor.Envelope(recipient, message) =>
        context
          .child(recipient.value.toString)
          .getOrElse(createAccountActor(recipient))
          .forward(message)
    }
  }

  val accountRepo = new InMemoryAccountRepository()
  val accountActors = system.actorOf(Props(new AccountSupervisor(accountRepo)))
  val accountRoutes = new AccountRoutes(accountActors)(system.dispatcher)

  "Creating an Account" should {
    "Adhere to the Json Contract" in {
      val accountHolder = generateAccountHolder()

      val request =
        s"""
          {
            "accountHolder":{
              "name": "${accountHolder.name}"
            }
          }
        """

      val response =
        s"""
          {
            "id":"ID",
            "balance":0,
            "accountHolder":{
              "name":"${accountHolder.name}"
            }
          }
         """

      val result = Post("/account")
        .withEntity(ContentTypes.`application/json`, request) ~> accountRoutes.routes ~> runRoute

      check {
        val json = JsonParser(
          entityAs[String].replaceFirst(
            """"id":".*?"""",
            """"id":"ID""""
          )
        )
        val expected = JsonParser(response)

        assert(json === expected)
      } (result)
    }
  }

  "Retrieving the Account" should {
    "Adhere to the Json Contract" in {
      val creditAmount1 = generateCreditAmount()
      val account = generateAccount(balance = creditAmount1)
      accountRepo.update(account)

      val response =
        s"""
          {
            "id":"${account.id.value.toString}",
            "balance":${creditAmount1.amount},
            "accountHolder":{
              "name":"${account.accountHolder.name}"
            }
          }
         """

      val result = Get(s"/account/${account.id.value.toString}") ~> accountRoutes.routes ~> runRoute

      check {
        val json = JsonParser(entityAs[String])
        val expected = JsonParser(response)

        assert(json === expected)
      } (result)
    }
  }

  "Crediting to an Account" should {
    "Adhere to the Json Contract" in {
      val creditAmount = generateCreditAmount()
      val account = generateAccount(balance = creditAmount.copy(amount = 0D))
      accountRepo.update(account)

      val request =
        s"""
          {
            "amount":${creditAmount.amount}
          }
        """

      val response =
        s"""
          {
            "id":"${account.id.value.toString}",
            "balance":${creditAmount.amount},
            "accountHolder":{
              "name":"${account.accountHolder.name}"
            }
          }
         """

      val result = Post(s"/account/${account.id.value.toString}/amount")
        .withEntity(ContentTypes.`application/json`, request) ~> accountRoutes.routes ~> runRoute

      check {
        val json = JsonParser(entityAs[String])
        val expected = JsonParser(response)

        assert(json === expected)
      } (result)
    }
  }
}
