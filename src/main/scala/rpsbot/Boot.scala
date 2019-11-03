package rpsbot

import cats.effect.{ExitCode, IO, IOApp}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import rpsbot.bot.RpsBotApp
import rpsbot.bot.persistence.{DynamoStorage, MemoryStorage}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import rpsbot.bot.communication.operations.{FeedbackTranslator, UserMessageParser}
import fs2.Stream

object Boot extends IOApp {
  lazy val telegramBotToken: IO[String]         = IO(sys.env("BOT_TOKEN"))
  lazy val persistenceTable: IO[Option[String]] = IO(sys.env.get("LOBBIES_DYNAMODB_TABLE"))
  lazy val interaction: IO[(UserMessageParser, FeedbackTranslator)] = IO(sys.env.get("RPS_LANG").map(_.toLowerCase)) map {
    case Some("de") => (MessageParserDE, FeedbackDE)
    case _          => (MessageParserEN, FeedbackEN)
  }

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      logger    <- Slf4jLogger.fromClass[IO](getClass)
      appStream <- appWithConfiguredStorage(logger)
      _         <- logger.info(s"Starting RPS bot application.")
      result <- appStream.compile.drain.map(_ => ExitCode.Success).recoverWith {
                 case t: Throwable => logger.error(t)("RpsBot application crashed") >> IO(ExitCode.Error)
               }
    } yield result
  }

  /**
    * Creates the RPS Bot App as an fs2 Stream by reading configuration environment variables, creating resources
    * accordingly and calling [[RpsBotApp.create]] with those resources.
    */
  private def appWithConfiguredStorage(logger: Logger[IO]): IO[Stream[IO, Unit]] =
    for {
      token              <- telegramBotToken
      (parser, feedback) <- interaction
      maybeTableName     <- persistenceTable
      storage <- maybeTableName match {
                  case None => logger.info("Will create app with In-Memory Storage") >> MemoryStorage.build[IO]()
                  case Some(tableName) =>
                    logger.info(s"Will create app with DynamoDB Storage, table $tableName") >> IO(DynamoStorage.build[IO](tableName))
                }
    } yield RpsBotApp.create(token, storage, parser, feedback)
}
