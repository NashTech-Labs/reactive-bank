package com.reactivebank.accounts

import java.util.UUID

object AccountNumber {
  def apply(): AccountNumber = AccountNumber(UUID.randomUUID())
}

case class AccountNumber(value: Long)

case class AccountHolder(name: String)
case class Table(number: Int)

case class CreditAmount(accountNumber: Long, amount: Double)

case class Account(
                    id: AccountNumber,
                    server: AccountHolder,
                    balance: Double
) extends SerializableMessage {

  def addBalance(amount: CreditAmount): Account = {
    this.copy(balance = balance + amount.amount)
  }
}
