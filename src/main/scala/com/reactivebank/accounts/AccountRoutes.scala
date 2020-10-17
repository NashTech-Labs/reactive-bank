package com.reactivebank.accounts

import java.util.UUID

import akka.actor.ActorRef
import akka.pattern.ask
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import com.reactivebank.accounts.AccountActor.{Envelope, GetAccount, AmountCreditedToAccount, AccountNotFoundException, AccountOpened}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class AccountRoutes(accountActors: ActorRef)(implicit ec: ExecutionContext)
  extends AccountJsonFormats {

  private implicit val timeout: Timeout = Timeout(5.seconds)

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: AccountNotFoundException =>
      complete(HttpResponse(StatusCodes.NotFound, entity = ex.getMessage))
    case ex =>
      complete(HttpResponse(StatusCodes.InternalServerError, entity = ex.getMessage))
  }

  lazy val routes: Route =
    handleExceptions(exceptionHandler) {
      pathPrefix("account") {
        post {
          entity(as[AccountActor.OpenAccount]) { openAccount =>
            complete {
              val accountNumber = AccountNumber()

              (accountActors ? Envelope(accountNumber, openAccount))
                .mapTo[AccountOpened]
                .map(_.account)
            }
          }
        } ~
        pathPrefix(Segment) { id =>

          val accountNumber = AccountNumber(id.toLong)

          get {
            complete {
              (accountActors ? Envelope(accountNumber, GetAccount())).mapTo[Account]
            }
          } ~
          path("amount") {
            post {
              entity(as[AccountActor.CreditAmountToAccount]) { creditAmountToAccount =>
                complete {
                  (accountActors ? Envelope(accountNumber, creditAmountToAccount))
                    .mapTo[AmountCreditedToAccount]
                    .map(_.account)
                }
              }
            }
          }
        }
      }
    }
}
