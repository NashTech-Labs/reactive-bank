package com.reactivebank.accounts

import akka.actor.Status
import akka.cluster.sharding.ShardRegion
import akka.testkit.TestProbe
import com.reactivebank.accounts.AccountActor._
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.Future

class OrderActorTest extends AnyWordSpec with AkkaSpec with OrderHelpers {

  class MockRepo extends InMemoryAccountRepository {
    private val updates: mutable.Queue[Function1[Account, Future[Account]]] = mutable
      .Queue()
    private val finds: mutable.Queue[Function1[AccountNumber, Future[Option[Account]]]] = mutable.Queue()

    override def update(order: Account) = {
      if(updates.nonEmpty)
        updates.dequeue()(order)
      else
        super.update(order)
    }

    override def find(orderId: AccountNumber) = {
      if(finds.nonEmpty)
        finds.dequeue()(orderId)
      else
        super.find(orderId)
    }

    def mockUpdate(u: Account => Future[Account]) = {
      updates.enqueue(u)
      this
    }

    def mockFind(f: AccountNumber => Future[Option[Account]]) = {
      finds.enqueue(f)
      this
    }
  }

  class TestContext() {
    val repo = new MockRepo()
    val orderId = generateOrderId()
    val sender = TestProbe()
    val parent = TestProbe()

    val orderActor = parent.childActorOf(
      AccountActor.props(repo),
      orderId.value.toString
    )

    def openOrder(): Account = {
      val server = generateServer()
      val table = generateTable()

      sender.send(orderActor, OpenAccount(server, table))
      sender.expectMsgType[AccountOpened].account
    }
  }

  "idExtractor" should {
    "return the expected id and message" in {
      val orderId = generateOrderId()
      val message = GetAccount()
      val envelope = Envelope(orderId, message)

      val result = entityIdExtractor(envelope)

      assert(result === (orderId.value.toString, message))
    }
  }

  "shardIdExtractor" should {
    "return the expected shard id" in {
      val orderId = generateOrderId()
      val message = GetAccount()
      val envelope = Envelope(orderId, message)
      val startEntity = ShardRegion.StartEntity(orderId.value.toString)

      val envelopeShard = shardIdExtractor(envelope)
      val startEntityShard = shardIdExtractor(startEntity)

      assert(envelopeShard === startEntityShard)
      assert(envelopeShard === Math.abs(orderId.value.toString.hashCode % 30).toString)
    }
  }

  "The Actor" should {
    "Load it's state from the repository when created." in new TestContext {
      val order = generateOrder()
      repo.update(order).futureValue

      val actor = parent
        .childActorOf(
          AccountActor.props(repo),
          order.id.value.toString)

      sender.send(actor, GetAccount())
      sender.expectMsg(order)
    }
    "Terminate when it fails to load from the repo" in new TestContext {
      val order = generateOrder()

      val mockRepo = new MockRepo()
      mockRepo.mockFind(_ => Future.failed(new Exception("Repo Failed")))

      val actor = parent
        .childActorOf(
          AccountActor.props(mockRepo),
          order.id.value.toString)

      parent.watch(actor)
      parent.expectTerminated(actor)
    }
  }

  "OpenOrder" should {
    "initialize the Order" in new TestContext {
      val server = generateServer()
      val table = generateTable()

      sender.send(orderActor, OpenAccount(server, table))
      val order = sender.expectMsgType[AccountOpened].account

      assert(repo.find(order.id).futureValue === Some(order))

      assert(order.server === server)
      assert(order.table === table)
    }
    "return an error if the Order is already opened" in new TestContext {
      val server = generateServer()
      val table = generateTable()

      sender.send(orderActor, OpenAccount(server, table))
      sender.expectMsgType[AccountOpened].account

      sender.send(orderActor, OpenAccount(server, table))
      sender.expectMsg(Status.Failure(DuplicateAccountException(orderId)))
    }
    "return the repository failure if the repository fails and fail" in new TestContext() {
      val server = generateServer()
      val table = generateTable()

      parent.watch(orderActor)

      val expectedException = new RuntimeException("Repository Failure")
      repo.mockUpdate(_ => Future.failed(expectedException))

      sender.send(orderActor, OpenAccount(server, table))
      val result = sender.expectMsg(Status.Failure(expectedException))

      parent.expectTerminated(orderActor)
    }
    "not allow further interactions while it's in progress" in new TestContext() {
      val order = generateOrder(orderId = orderId, items = Seq.empty)

      repo.mockUpdate {
        order => Future {
          Thread.sleep(50)
          order
        }
      }

      sender.send(orderActor, OpenAccount(order.server, order.table))
      sender.send(orderActor, OpenAccount(order.server, order.table))

      sender.expectMsg(AccountOpened(order))
      sender.expectMsg(Status.Failure(DuplicateAccountException(orderId)))
    }
  }

  "AddItemToOrder" should {
    "return an OrderNotFoundException if the order hasn't been Opened." in new TestContext {
      val item = generateOrderItem()

      sender.send(orderActor, CreditAmountToAccount(item))
      sender.expectMsg(Status.Failure(AccountNotFoundException(orderId)))
    }
    "add the item to the order" in new TestContext {
      val order = openOrder()

      val item = generateOrderItem()

      sender.send(orderActor, CreditAmountToAccount(item))
      sender.expectMsg(AmountCreditedToAccount(order.addBalance(item)))
    }
    "add multiple items to the order" in new TestContext {
      val order = openOrder()

      val items = generateOrderItems(10)

      items.foldLeft(order) {
        case (prevOrder, item) =>
          val updated = prevOrder.addBalance(item)

          sender.send(orderActor, CreditAmountToAccount(item))
          sender.expectMsg(AmountCreditedToAccount(updated))

          updated
      }
    }
    "return the repository failure if the repository fails and fail" in new TestContext() {
      val order = openOrder()

      parent.watch(orderActor)

      val item = generateOrderItem()

      val expectedException = new Exception("Repository Failure")
      repo.mockUpdate(_ => Future.failed(expectedException))

      sender.send(orderActor, CreditAmountToAccount(item))
      sender.expectMsg(Status.Failure(expectedException))

      parent.expectTerminated(orderActor)
    }
    "not allow further interactions while it's in progress" in new TestContext() {
      val order = openOrder()

      val item1 = generateOrderItem()
      val updated1 = order.addBalance(item1)

      val item2 = generateOrderItem()
      val updated2 = updated1.addBalance(item2)

      repo.mockUpdate {
        order => Future {
          Thread.sleep(50)
          order
        }
      }

      sender.send(orderActor, CreditAmountToAccount(item1))
      sender.send(orderActor, CreditAmountToAccount(item2))

      sender.expectMsg(AmountCreditedToAccount(updated1))
      sender.expectMsg(AmountCreditedToAccount(updated2))
    }
  }

  "GetOrder" should {
    "return an OrderNotFoundException if the order hasn't been Opened." in new TestContext {
      sender.send(orderActor, GetAccount())
      sender.expectMsg(Status.Failure(AccountNotFoundException(orderId)))
    }
    "return an open order" in new TestContext {
      val order = openOrder()

      sender.send(orderActor, GetAccount())
      sender.expectMsg(order)
    }
    "return an updated order" in new TestContext {
      val order = openOrder()
      val item = generateOrderItem()

      sender.send(orderActor, CreditAmountToAccount(item))
      sender.expectMsgType[AmountCreditedToAccount].account

      sender.send(orderActor, GetAccount())
      sender.expectMsg(order.addBalance(item))
    }
  }
}
