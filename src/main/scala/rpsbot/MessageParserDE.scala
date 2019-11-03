package rpsbot

import kaleidoscope._
import rpsbot.bot.communication.model._
import rpsbot.bot.communication.operations.UserMessageParser
import rpsbot.rps.model.{Paper, Rock, Scissors}

import scala.util.Try

object MessageParserDE extends UserMessageParser {
  override def parse(in: String): UserMessage = in.toLowerCase.trim match {
    case r"/?help" => Help
    case r"/start" => Help

    case r"/?schnick\s+$lobbyId@(\d+)" =>
      JoinLobby(Try(lobbyId.asInstanceOf[String].toInt).get) // TODO is it possibly to get rid of the type cast to string?
    case r"/?schnick" => CreateLobby

    case r"/?stein" => Play(Rock)
    case r"/?rock"  => Play(Rock)

    case r"/?papier" => Play(Paper)
    case r"/?paper"  => Play(Paper)

    case r"/?schere" => Play(Scissors)
    case r"/?schere" => Play(Scissors)

    case _ => Unknown
  }
}
