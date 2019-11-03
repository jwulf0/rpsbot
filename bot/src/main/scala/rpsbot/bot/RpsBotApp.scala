package rpsbot.bot

import canoe.api.{Bot, TelegramClient, pipes}
import canoe.models.PrivateChat
import canoe.models.messages.{TelegramMessage, TextMessage}
import cats.effect.{ConcurrentEffect, Timer}
import fs2.Stream
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import rpsbot.bot.communication.UserMessageHandler
import rpsbot.bot.communication.model.{UserMessage, UserMessageWithContext}
import rpsbot.bot.communication.operations.{FeedbackTranslator, UserMessageParser}
import rpsbot.bot.persistence.Storage
import rpsbot.bot.util.stream.retryPipe

import scala.concurrent.duration._

object RpsBotApp {

  /**
    * This method builds the [[UserMessageHandler]] and the fs2 stream from polling the Telegram Bot API to the handler.
    */
  def create[F[_]: ConcurrentEffect: Timer](botToken: String,
                                            storage: Storage[F],
                                            userMessageParser: UserMessageParser,
                                            feedbackToMessage: FeedbackTranslator): Stream[F, Unit] = {

    // The TelegramClient is provided as [[cats.effect.Resource]]
    Stream.resource(TelegramClient.global[F](botToken)).flatMap { implicit telegramClient =>
      lazy val collectMessages: PartialFunction[TelegramMessage, UserMessageWithContext] = {
        case m @ TextMessage(_, PrivateChat(_, _, _, _), _, _, _, Some(user), _, _, _, _, _, _, _, _, _) =>
          val parsed: UserMessage = userMessageParser.parse(m.text)
          UserMessageWithContext(parsed, m, user)
      }

      for {
        logger  <- Stream.eval(Slf4jLogger.fromClass(getClass))
        handler <- Stream.eval(UserMessageHandler.create(storage, feedbackToMessage))
        _       <- Stream.eval(logger.info("Will start polling Bot API for updates"))
        _ <- Bot
              .polling[F]
              .updates
              .through(retryPipe(5 seconds, 10, t => logger.error(t)("== Error in Updates Stream =="))) // pause 5 seconds after each error (which might be a resource conflict regarding the bot token) and retry max 10 times (in case it's another problem like a wrong token)
              .through(pipes.messages)
              .collect(collectMessages)
              .parEvalMap(4)(handler.handle)
      } yield ()
    }
  }
}
