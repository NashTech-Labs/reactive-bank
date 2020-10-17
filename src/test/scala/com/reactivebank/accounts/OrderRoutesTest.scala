package com.reactivebank.accounts

import akka.actor.Status
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ContentTypes, MessageEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import com.reactivebank.accounts.AccountActor.AccountNotFoundException
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec

class OrderRoutesTest
  extends AnyWordSpec
    with ScalatestRouteTest
    with OrderHelpers
    with AccountJsonFormats
    with ScalaFutures {

  val orders = TestProbe()
  val http = new AccountRoutes(orders.ref)

  "POST to /order" should {
    "create a new order" in {
      val order = generateOrder()
      val request = AccountActor.OpenAccount(order.server, order.table)

      val result = Post("/order")
        .withEntity(Marshal(request).to[MessageEntity].futureValue) ~>
        http.routes ~>
        runRoute

      orders.expectMsgPF() {
        case AccountActor.Envelope(_, AccountActor.OpenAccount(server, stable)) =>
          assert(server === order.server)
          assert(stable === order.table)
        case msg => fail("Unexpected Message: "+msg)
      }

      orders.reply(AccountActor.AccountOpened(order))

      check {
        assert(status === StatusCodes.OK)
        assert(contentType === ContentTypes.`application/json`)
        assert(entityAs[Account] === order)
      } (result)
    }
  }

  "GET to /order/{id}" should {
    "return the order" in {
      val order = generateOrder()
      val result = Get(s"/order/${order.id.value.toString}") ~>
        http.routes ~>
        runRoute

      orders.expectMsg(AccountActor.Envelope(order.id, AccountActor.GetAccount()))
      orders.reply(order)

      check {
        assert(status === StatusCodes.OK)
        assert(contentType === ContentTypes.`application/json`)
        assert(entityAs[Account] === order)
      }(result)
    }
    "return a meaningful error if the order doesn't exist" in {
      val orderId = generateOrderId()
      val result = Get(s"/order/${orderId.value.toString}") ~>
        http.routes ~>
        runRoute

      val expectedError = AccountActor.AccountNotFoundException(orderId)

      orders.expectMsg(AccountActor.Envelope(orderId, AccountActor.GetAccount()))
      orders.reply(Status.Failure(expectedError))

      check {
        assert(status === StatusCodes.NotFound)
        assert(contentType === ContentTypes.`text/plain(UTF-8)`)
        assert(entityAs[String] === expectedError.getMessage)
      }(result)
    }
  }

  "POST to /order/{id}/items" should {
    "add an item to the order" in {
      val item = generateOrderItem()
      val order = generateOrder().addBalance(item)
      val request = AccountActor.CreditAmountToAccount(item)

      val result = Post(s"/order/${order.id.value.toString}/items")
        .withEntity(Marshal(request).to[MessageEntity].futureValue) ~>
        http.routes ~>
        runRoute

      orders.expectMsg(AccountActor.Envelope(order.id, request))
      orders.reply(AccountActor.AmountCreditedToAccount(order))

      check {
        assert(status === StatusCodes.OK)
        assert(contentType === ContentTypes.`application/json`)
        assert(entityAs[Account] === order)
      }(result)
    }
    "return a meaningful error if the order doesn't exist" in {
      val orderId = generateOrderId()
      val item = generateOrderItem()
      val request = AccountActor.CreditAmountToAccount(item)

      val expectedError = AccountNotFoundException(orderId)

      val result = Post(s"/order/${orderId.value.toString}/items")
        .withEntity(Marshal(request).to[MessageEntity].futureValue) ~>
        http.routes ~>
        runRoute

      orders.expectMsg(AccountActor.Envelope(orderId, request))
      orders.reply(Status.Failure(expectedError))

      check {
        assert(status === StatusCodes.NotFound)
        assert(contentType === ContentTypes.`text/plain(UTF-8)`)
        assert(entityAs[String] === expectedError.getMessage)
      }(result)    }
  }

}
