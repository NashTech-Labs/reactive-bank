package com.reactivebank.accounts

import org.scalatest.wordspec.AnyWordSpec

class OrderTest extends AnyWordSpec with OrderHelpers {

  "withItem" should {
    "return a copy of the Order with the new item when no previous items exist" in {
      val orderItem = generateOrderItem()

      val order = generateOrder(items = Seq.empty)
      val updated = order.addBalance(orderItem)

      assert(order.items === Seq.empty)
      assert(updated.items === Seq(orderItem))
    }
    "return a copy of the Order with the new item appended when previous items exist" in {
      val oldItems = generateOrderItems(10)
      val newItem = generateOrderItem()

      val order = generateOrder(items = oldItems)
      val updated = order.addBalance(newItem)

      assert(order.items === oldItems)
      assert(updated.items === oldItems :+ newItem)
    }
  }
}
