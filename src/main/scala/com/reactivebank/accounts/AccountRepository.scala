package com.reactivebank.accounts

import javax.persistence._

import scala.concurrent.{ExecutionContext, Future}

trait AccountRepository {
  def update(account: Account): Future[Account]
  def find(accountNumber: AccountNumber): Future[Option[Account]]
}

class InMemoryAccountRepository()(implicit ec: ExecutionContext) extends AccountRepository {
  private var accounts = Map.empty[AccountNumber, Account]

  override def update(account: Account): Future[Account] = {
    Future {
      synchronized {
        accounts = accounts.updated(account.id, account)
      }
      account
    }
  }

  override def find(accountNumber: AccountNumber): Future[Option[Account]] = {
    Future {
      synchronized {
        accounts.get(accountNumber)
      }
    }
  }
}

@Embeddable
class CreditAmountDBO() {
  private var amount: Double = 0D

  def getAmount: Double = amount

  def apply(creditAmount: CreditAmount): CreditAmountDBO = {
    amount = creditAmount.amount

    this
  }
}

@Entity
@javax.persistence.Table(name = "accounts")
class AccountDBO() {

  @Id
  private var id: Long = _

  private var accountHolderName: String = _

  @ElementCollection(targetClass = classOf[CreditAmountDBO])
  private var balance: CreditAmountDBO = _

  def getAccountHolderName: String = accountHolderName
  def getBalance: CreditAmountDBO = balance

  def apply(account: Account): AccountDBO = {
    id = account.id.value
    accountHolderName = account.accountHolder.name
    balance = new CreditAmountDBO().apply(new CreditAmount(id, account.balance))

    this
  }
}

class SQLAccountRepository()(implicit ec: ExecutionContext) extends AccountRepository {

  private val entityManagerFactory = Persistence.createEntityManagerFactory("reactivebanking.accounts")

  private val threadLocalEntityManager = new ThreadLocal[EntityManager]()

  private def getEntityManager: EntityManager = {
    if(threadLocalEntityManager.get() == null) {
      threadLocalEntityManager.set(entityManagerFactory.createEntityManager())
    }

    threadLocalEntityManager.get()
  }

  private def transaction[A](f: EntityManager => A): A = {
    val entityManager = getEntityManager

    entityManager.getTransaction.begin()
    val result = f(entityManager)
    entityManager.getTransaction.commit()
    result
  }

  override def update(account: Account): Future[Account] = Future {
    val dbo = new AccountDBO().apply(account)
    transaction(_.merge(dbo))
    account
  }

  override def find(accountNumber: AccountNumber): Future[Option[Account]] = Future {
    transaction { t =>
      Option(t.find(classOf[AccountDBO], accountNumber.value))
        .map(dbo => Account(
          accountNumber,
          AccountHolder(dbo.getAccountHolderName),
          dbo.getBalance.getAmount
        ))
    }
  }
}
