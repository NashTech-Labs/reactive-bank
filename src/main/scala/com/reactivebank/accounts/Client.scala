package com.reactivebank.accounts

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MessageEntity}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}

object Client extends App with AccountJsonFormats {
  val usage =
    """
      | USAGE:
      |     sbt 'runMain com.reactivebank.accounts.Client open <accountHolderName>'
      |     sbt 'runMain com.reactivebank.accounts.Client credit <accountNumber> <amount>'
      |     sbt 'runMain com.reactivebank.accounts.Client debit <accountNumber> <amount>'
      |     sbt 'runMain com.reactivebank.accounts.Client find <accountNumber>'
    """.stripMargin

  require(args.length > 0, usage)

  val config = ConfigFactory.load("client.conf")

  implicit val system: ActorSystem = ActorSystem("client", config)
  implicit val ec: ExecutionContext = system.dispatcher

  val ports = Random.shuffle(Seq(8000, 8001, 8002)).iterator

  def port = {
    if(ports.hasNext)
      ports.next()
    else
      throw new RuntimeException("Tried all available ports without success.")
  }

  val result = args(0) match {
    case "open" =>
      runIf(args.length == 2) {
        openAccount(args(1))
      }
    case "credit" =>
      runIf(args.length == 3) {
        creditAmount(args(1).toLong, args(2).toDouble)
      }
    case "find" =>
      runIf(args.length == 1) {
        findAccount(args(1).toLong)
      }
  }

  result.andThen {
    case Success(order) =>
      system.log.info(order.toString)
      system.terminate()
    case Failure(ex) =>
      system.log.error(ex, "Failure")
      system.terminate()
  }

  private def runIf(requirement: Boolean)(run: => Future[String]): Future[String] = {
    if(!requirement) {
      Future.failed(new IllegalArgumentException("Invalid Command: \n" + usage))
    } else {
      run
    }
  }

  private def openAccount(accountHolderName: String): Future[String] = {
    val command = AccountActor.OpenAccount(AccountHolder(accountHolderName))
    val url = s"http://localhost:$port/account"

    (for {
      requestEntity <- Marshal(command).to[MessageEntity]
      request = HttpRequest(HttpMethods.POST, url, entity = requestEntity)
      response <- Http().singleRequest(request)
      responseEntity <- response.entity.toStrict(5.seconds)
    } yield {
      new String(responseEntity.data.toArray)
    }).recoverWith {
      case _ =>
        system.log.warning(s"Attempt to connect to $url failed. Retrying.")
        openAccount(accountHolderName)
    }
  }

  private def creditAmount(accountNumber: Long, amount: Double): Future[String] = {
    val command = AccountActor.CreditAmountToAccount(CreditAmount(accountNumber, amount))
    val url = s"http://localhost:$port/account/$accountNumber/amount"

    (for {
      requestEntity <- Marshal(command).to[MessageEntity]
      request = HttpRequest(HttpMethods.POST, url, entity = requestEntity)
      response <- Http().singleRequest(request)
      responseEntity <- response.entity.toStrict(5.seconds)
    } yield {
      new String(responseEntity.data.toArray)
    }).recoverWith {
      case _ =>
        system.log.warning(s"Attempt to connect to $url failed. Retrying.")
        creditAmount(accountNumber, amount)
    }
  }

  private def findAccount(accountNumber: Long): Future[String] = {
    val url = s"http://localhost:$port/account/$accountNumber"

    (for {
      response <- Http().singleRequest(HttpRequest(HttpMethods.GET, url))
      responseEntity <- response.entity.toStrict(5.seconds)
    } yield {
      new String(responseEntity.data.toArray)
    }).recoverWith {
      case _ =>
        system.log.warning(s"Attempt to connect to $url failed. Retrying.")
        findAccount(accountNumber)
    }
  }

}
