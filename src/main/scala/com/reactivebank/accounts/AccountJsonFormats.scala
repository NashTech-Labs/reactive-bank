package com.reactivebank.accounts

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat}

trait AccountJsonFormats extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val accountNumberFormat = new JsonFormat[AccountNumber] {
    def write(accountNumber: AccountNumber) = JsString(accountNumber.value.toString)
    def read(value: JsValue): AccountNumber = {
      value match {
        case JsString(uuid) => AccountNumber(uuid.toLong)
        case _              => throw DeserializationException("Expected UUID string")
      }
    }
  }

  implicit val serverFormat = jsonFormat1(AccountHolder)
  implicit val tableFormat = jsonFormat1(Table)
  implicit val creditAmountFormat = jsonFormat2(CreditAmount)
  implicit val accountFormat = jsonFormat3(Account)
  implicit val openOrderFormat = jsonFormat1(AccountActor.OpenAccount)
  implicit val creditAmountToAccountFormat = jsonFormat1(AccountActor.CreditAmountToAccount)
}
