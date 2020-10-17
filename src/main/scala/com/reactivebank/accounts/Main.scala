package com.reactivebank.accounts

import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.http.scaladsl.Http
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory

object Main extends App {
  val log = LoggerFactory.getLogger(this.getClass)

  val Opt = """-D(\S+)=(\S+)""".r
  args.toList.foreach {
    case Opt(key, value) =>
      log.info(s"Config Override: $key = $value")
      System.setProperty(key, value)
  }

  implicit val system: ActorSystem = ActorSystem("Accounts")

  AkkaManagement(system).start()

  val blockingDispatcher = system.dispatchers.lookup("blocking-dispatcher")
  val accountRepository: AccountRepository = new SQLAccountRepository()(blockingDispatcher)

  val accounts = ClusterSharding(system).start(
    "accounts",
    AccountActor.props(accountRepository),
    ClusterShardingSettings(system),
    AccountActor.entityIdExtractor,
    AccountActor.shardIdExtractor
  )

  val accountRoutes = new AccountRoutes(accounts)(system.dispatcher)

  Http().newServerAt("localhost", 8558).bindFlow(accountRoutes.routes)
}
