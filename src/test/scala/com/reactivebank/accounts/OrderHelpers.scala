package com.reactivebank.accounts

import scala.util.Random

trait OrderHelpers {
  private val rnd = new Random(System.currentTimeMillis())
  private val rndInts = (1 to 10000).map(_ => rnd.nextInt(1000000)).toSet.iterator

  def generateOrderId(): AccountNumber = AccountNumber()
  def generateServer(): AccountHolder = AccountHolder("ServerName"+rndInts.next())
  def generateTable(): Table = Table(rndInts.next())

  def generateOrderItem(
    name: String = "ItemName"+rndInts.next(),
    specialInstructions: String = "SpecialInstructions"+rndInts.next()
  ): CreditAmount = {
    CreditAmount(
      name,
      specialInstructions
    )
  }

  def generateOrderItems(quantity: Int = rnd.nextInt(10)): Seq[CreditAmount] = {
    (1 to quantity).map(_ => generateOrderItem())
  }

  def generateOrder(
                     orderId: AccountNumber = generateOrderId(),
                     serverId: AccountHolder = generateServer(),
                     tableNumber: Table = generateTable(),
                     items: Seq[CreditAmount] = generateOrderItems()
  ): Account = Account(
    orderId,
    serverId,
    tableNumber,
    items
  )
}
