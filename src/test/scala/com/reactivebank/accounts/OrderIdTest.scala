package com.reactivebank.accounts

import org.scalatest.wordspec.AnyWordSpec

class OrderIdTest extends AnyWordSpec {

  "apply" should {
    "generate a unique id each time it is called" in {
      val ids = (1 to 10).map(_ => AccountNumber())

      assert(ids.toSet.size === ids.size)
      assert(ids.map(_.value).toSet.size === ids.map(_.value).size)
    }
  }
}
