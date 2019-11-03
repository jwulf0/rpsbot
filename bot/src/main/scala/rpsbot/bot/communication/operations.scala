package rpsbot.bot.communication

import canoe.models.outgoing.TextContent
import rpsbot.bot.communication.model.{Feedback, UserMessage}

/**
  * While [[model]] defines, what types of messages users can send to the application ([[UserMessage]]) and vice versa
  * ([[Feedback]]), the specific phrases are provided by implementations of the two traits in this package.
  *
  * With them, ... such as internationalized versions, are possible.
  */
object operations {

  /**
    * ... . [[rpsbot.bot.RpsBotApp]] ensures that only text messages in private chats ever reach this parser. Note that unparsed messages should return [[rpsbot.bot.communication.model.Unknown]].
    */
  trait UserMessageParser {
    def parse(in: String): UserMessage
  }

  /**
    * Translates the rps bot app's possible output, represented by [[Feedback]], into [[TextContent]] which will be sent
    * to the user by the application.
    */
  trait FeedbackTranslator {
    def apply(f: Feedback): TextContent
  }
}
