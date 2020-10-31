package com.reactivebank.accounts

import org.scalatest.wordspec.AnyWordSpec

class AccountTest extends AnyWordSpec with AccountHelpers {

  "withAmount" should {
    "return a copy of the Account with the new balance when no previous balance exist" in {
      val creditAmount = generateCreditAmount()

      val account = generateAccount(balance = CreditAmount(creditAmount.accountNumber, 0D))
      val updated = account.addBalance(creditAmount)

      assert(account.balance === 0D)
      assert(updated.balance === creditAmount.amount)
    }
    "return a copy of the Account with the new balance appended when previous balance exist" in {
      val oldBalance = generateAccountBalance(10)
      val newBalance = generateCreditAmount()

      val account = generateAccount(balance = oldBalance)
      val updated = account.addBalance(newBalance)

      assert(account.balance === oldBalance.amount)
      assert(updated.balance === oldBalance.amount + newBalance.amount)
    }
  }
}
