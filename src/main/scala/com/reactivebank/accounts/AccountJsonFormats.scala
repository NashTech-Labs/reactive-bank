package com.reactivebank.accounts

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}

trait AccountJsonFormats extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val orderIdFormat = new JsonFormat[AccountNumber] {
    def write(orderId: AccountNumber) = JsString(orderId.value.toString)
    def read(value: JsValue): AccountNumber = {
      value match {
        case JsString(uuid) => AccountNumber(UUID.fromString(uuid))
        case _              => throw DeserializationException("Expected UUID string")
      }
    }
  }

  implicit val serverFormat = jsonFormat1(AccountHolder)
  implicit val tableFormat = jsonFormat1(Table)
  implicit val orderItemFormat = jsonFormat2(CreditAmount)
  implicit val orderFormat = jsonFormat4(Account)
  implicit val openOrderFormat = jsonFormat2(AccountActor.OpenAccount)
  implicit val addItemToOrderFormat = jsonFormat1(AccountActor.CreditAmountToAccount)
}
