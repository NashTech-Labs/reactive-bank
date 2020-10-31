package com.reactivebank.accounts

import akka.actor.{Actor, ActorLogging, Props, Stash, Status}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.pattern.pipe

import scala.concurrent.Future

object AccountActor {
  sealed trait Command extends SerializableMessage

  case class OpenAccount(accountHolderName: AccountHolder) extends Command
  case class AccountOpened(account: Account) extends SerializableMessage
  case class CreditAmountToAccount(creditAmount: CreditAmount) extends Command
  case class AmountCreditedToAccount(account: Account) extends SerializableMessage
  case class GetAccount() extends Command

  case class AccountNotFoundException(accountNumber: AccountNumber) extends IllegalStateException(s"Account Not Found: $accountNumber")
  case class DuplicateAccountException(accountNumber: AccountNumber) extends IllegalStateException(s"Duplicate Account: $accountNumber")

  case class Envelope(accountNumber: AccountNumber, command: Command) extends SerializableMessage

  private case class AccountLoaded(account: Option[Account])

  val entityIdExtractor: ExtractEntityId = {
    case Envelope(accountNumber, command) => (accountNumber.value.toString, command)
  }

  val shardIdExtractor: ExtractShardId = {
    case Envelope(accountNumber, _) => Math.abs(accountNumber.value.toString.hashCode % 30).toString
    case ShardRegion.StartEntity(entityId) => Math.abs(entityId.hashCode % 30).toString
  }

  def props(repository: AccountRepository): Props = Props(new AccountActor(repository))
}

class AccountActor(repository: AccountRepository) extends Actor with ActorLogging with Stash {
  import AccountActor._
  import context.dispatcher

  private val accountNumber: AccountNumber = AccountNumber(context.self.path.name.toLong)

  private var state: Option[Account] = None

  repository.find(accountNumber).map(AccountLoaded.apply).pipeTo(self)

  override def receive: Receive = loading

  private def loading: Receive = {
    case AccountLoaded(account) =>
      unstashAll()
      state = account
      context.become(running)
    case Status.Failure(ex) =>
      log.error(ex, s"{$accountNumber} FAILURE: ${ex.getMessage}")
      throw ex
    case _ => stash()
  }

  private def running: Receive = {
    case OpenAccount(accountHolderName) =>
      log.info(s"[$accountNumber] OpenAccount($accountHolderName)")

      state match {
        case Some(_) => duplicateAccount(accountNumber).pipeTo(sender())
        case None =>
          context.become(waiting)
          openAccount(accountNumber, accountHolderName).pipeTo(self)(sender())
      }

    case CreditAmountToAccount(amount) =>
      log.info(s"[$accountNumber] CreditAmountToAccount($amount)")

      state match {
        case Some(account) =>
          context.become(waiting)
          creditAmount(account, amount).pipeTo(self)(sender())
        case None => accountNotFound(accountNumber).pipeTo(sender())
      }

    case GetAccount() =>
      log.info(s"[$accountNumber] GetOrder()")

      state match {
        case Some(account) => sender() ! account
        case None => accountNotFound(accountNumber).pipeTo(sender())
      }
  }

  private def waiting: Receive = {
    case evt @ AccountOpened(account) =>
      state = Some(account)
      unstashAll()
      sender() ! evt
      context.become(running)
    case evt @ AmountCreditedToAccount(account) =>
      state = Some(account)
      unstashAll()
      sender() ! evt
      context.become(running)
    case failure @ Status.Failure(ex) =>
      log.error(ex, s"{$accountNumber} FAILURE: ${ex.getMessage}")
      sender() ! failure
      throw ex
    case _ => stash()
  }

  private def openAccount(accountNumber: AccountNumber, accountHolder: AccountHolder): Future[AccountOpened] = {
    repository.update(Account(accountNumber, accountHolder, 0D)).map(AccountOpened.apply)
  }

  private def duplicateAccount[T](accountNumber: AccountNumber): Future[T] = {
    Future.failed(DuplicateAccountException(accountNumber))
  }

  private def creditAmount(account: Account, creditAmount: CreditAmount): Future[AmountCreditedToAccount] = {
    repository.update(account.addBalance(creditAmount)).map(AmountCreditedToAccount.apply)
  }

  private def accountNotFound[T](accountNumber: AccountNumber): Future[T] = {
    Future.failed(AccountNotFoundException(accountNumber))
  }
}
