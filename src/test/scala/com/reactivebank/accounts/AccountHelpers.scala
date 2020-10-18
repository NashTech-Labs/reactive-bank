package com.reactivebank.accounts

import scala.util.Random

trait AccountHelpers {
  private val rnd = new Random(System.currentTimeMillis())
  private val rndInts = (1 to 10000).map(_ => rnd.nextInt(1000000)).toSet.iterator

  def generateAccountNumber(): AccountNumber = AccountNumber()
  def generateAccountHolder(): AccountHolder = AccountHolder("ServerName"+rndInts.next())

  def generateCreditAmount(
                            accountNumber: Long = rnd.nextLong(),
                            amount: Double = rnd.nextDouble()
  ): CreditAmount = {
    CreditAmount(
      accountNumber,
      amount
    )
  }

  def generateAccountBalance(quantity: Int = rnd.nextInt(10)): CreditAmount = {
    generateCreditAmount()
  }

  def generateAccount(
                       accountNumber: AccountNumber = generateAccountNumber(),
                       accountHolder: AccountHolder = generateAccountHolder(),
                       balance: CreditAmount = generateAccountBalance()
  ): Account = Account(
    accountNumber,
    accountHolder,
    balance.amount
  )
}
