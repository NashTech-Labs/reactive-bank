package com.reactivebank.accounts

import akka.actor.Status
import akka.cluster.sharding.ShardRegion
import akka.testkit.TestProbe
import com.reactivebank.accounts.AccountActor._
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.Future

class AccountActorTest extends AnyWordSpec with AkkaSpec with AccountHelpers {

  class MockRepo extends InMemoryAccountRepository {
    private val updates: mutable.Queue[Function1[Account, Future[Account]]] = mutable
      .Queue()
    private val finds: mutable.Queue[Function1[AccountNumber, Future[Option[Account]]]] = mutable.Queue()

    override def update(account: Account) = {
      if(updates.nonEmpty)
        updates.dequeue()(account)
      else
        super.update(account)
    }

    override def find(accountNumber: AccountNumber) = {
      if(finds.nonEmpty)
        finds.dequeue()(accountNumber)
      else
        super.find(accountNumber)
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
    val accountNumber = generateAccountNumber()
    val sender = TestProbe()
    val parent = TestProbe()

    val accountActor = parent.childActorOf(
      AccountActor.props(repo),
      accountNumber.value.toString
    )

    def openAccount(): Account = {
      val accountHolder = generateAccountHolder()

      sender.send(accountActor, OpenAccount(accountHolder))
      sender.expectMsgType[AccountOpened].account
    }
  }

  "idExtractor" should {
    "return the expected id and message" in {
      val accountNumber = generateAccountNumber()
      val message = GetAccount()
      val envelope = Envelope(accountNumber, message)

      val result = entityIdExtractor(envelope)

      assert(result === (accountNumber.value.toString, message))
    }
  }

  "shardIdExtractor" should {
    "return the expected shard id" in {
      val accountNumber = generateAccountNumber()
      val message = GetAccount()
      val envelope = Envelope(accountNumber, message)
      val startEntity = ShardRegion.StartEntity(accountNumber.value.toString)

      val envelopeShard = shardIdExtractor(envelope)
      val startEntityShard = shardIdExtractor(startEntity)

      assert(envelopeShard === startEntityShard)
      assert(envelopeShard === Math.abs(accountNumber.value.toString.hashCode % 30).toString)
    }
  }

  "The Actor" should {
    "Load it's state from the repository when created." in new TestContext {
      val account = generateAccount()
      repo.update(account).futureValue

      val actor = parent
        .childActorOf(
          AccountActor.props(repo),
          account.id.value.toString)

      sender.send(actor, GetAccount())
      sender.expectMsg(account)
    }
    "Terminate when it fails to load from the repo" in new TestContext {
      val account = generateAccount()

      val mockRepo = new MockRepo()
      mockRepo.mockFind(_ => Future.failed(new Exception("Repo Failed")))

      val actor = parent
        .childActorOf(
          AccountActor.props(mockRepo),
          account.id.value.toString)

      parent.watch(actor)
      parent.expectTerminated(actor)
    }
  }

  "OpenAccount" should {
    "open the Account" in new TestContext {
      val accountHolder = generateAccountHolder()

      sender.send(accountActor, OpenAccount(accountHolder))
      val order = sender.expectMsgType[AccountOpened].account

      assert(repo.find(order.id).futureValue === Some(order))

      assert(order.accountHolder === accountHolder)
    }
    "return an error if the Account is already opened" in new TestContext {
      val accountHolder = generateAccountHolder()

      sender.send(accountActor, OpenAccount(accountHolder))
      sender.expectMsgType[AccountOpened].account

      sender.send(accountActor, OpenAccount(accountHolder))
      sender.expectMsg(Status.Failure(DuplicateAccountException(accountNumber)))
    }
    "return the repository failure if the repository fails and fail" in new TestContext() {
      val accountHolder = generateAccountHolder()

      parent.watch(accountActor)

      val expectedException = new RuntimeException("Repository Failure")
      repo.mockUpdate(_ => Future.failed(expectedException))

      sender.send(accountActor, OpenAccount(accountHolder))
      val result = sender.expectMsg(Status.Failure(expectedException))

      parent.expectTerminated(accountActor)
    }
    "not allow further interactions while it's in progress" in new TestContext() {
      val account = generateAccount(accountNumber = accountNumber)

      repo.mockUpdate {
        order => Future {
          Thread.sleep(50)
          order
        }
      }

      sender.send(accountActor, OpenAccount(account.accountHolder))
      sender.send(accountActor, OpenAccount(account.accountHolder))

      sender.expectMsg(AccountOpened(account))
      sender.expectMsg(Status.Failure(DuplicateAccountException(accountNumber)))
    }
  }

  "CreditAmountToAccount" should {
    "return an AccountNotFoundException if the account hasn't been Opened." in new TestContext {
      val creditAmount = generateCreditAmount()

      sender.send(accountActor, CreditAmountToAccount(creditAmount))
      sender.expectMsg(Status.Failure(AccountNotFoundException(accountNumber)))
    }
    "credit the amount to the account" in new TestContext {
      val account = openAccount()

      val creditAmount = generateCreditAmount()

      sender.send(accountActor, CreditAmountToAccount(creditAmount))
      sender.expectMsg(AmountCreditedToAccount(account.addBalance(creditAmount)))
    }
    "return the repository failure if the repository fails and fail" in new TestContext() {
      val account = openAccount()

      parent.watch(accountActor)

      val creditAmount = generateCreditAmount()

      val expectedException = new Exception("Repository Failure")
      repo.mockUpdate(_ => Future.failed(expectedException))

      sender.send(accountActor, CreditAmountToAccount(creditAmount))
      sender.expectMsg(Status.Failure(expectedException))

      parent.expectTerminated(accountActor)
    }
    "not allow further interactions while it's in progress" in new TestContext() {
      val account = openAccount()

      val creditAmount1 = generateCreditAmount()
      val updated1 = account.addBalance(creditAmount1)

      val creditAmount2 = generateCreditAmount()
      val updated2 = updated1.addBalance(creditAmount2)

      repo.mockUpdate {
        account => Future {
          Thread.sleep(50)
          account
        }
      }

      sender.send(accountActor, CreditAmountToAccount(creditAmount1))
      sender.send(accountActor, CreditAmountToAccount(creditAmount2))

      sender.expectMsg(AmountCreditedToAccount(updated1))
      sender.expectMsg(AmountCreditedToAccount(updated2))
    }
  }

  "GetAccount" should {
    "return an AccountNotFoundException if the account hasn't been Opened." in new TestContext {
      sender.send(accountActor, GetAccount())
      sender.expectMsg(Status.Failure(AccountNotFoundException(accountNumber)))
    }
    "return an open account" in new TestContext {
      val account = openAccount()

      sender.send(accountActor, GetAccount())
      sender.expectMsg(account)
    }
    "return an updated account" in new TestContext {
      val account = openAccount()
      val creditAmount = generateCreditAmount()

      sender.send(accountActor, CreditAmountToAccount(creditAmount))
      sender.expectMsgType[AmountCreditedToAccount].account

      sender.send(accountActor, GetAccount())
      sender.expectMsg(account.addBalance(creditAmount))
    }
  }
}
