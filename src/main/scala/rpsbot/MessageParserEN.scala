package rpsbot

import kaleidoscope._
import rpsbot.bot.communication.model._
import rpsbot.bot.communication.operations.UserMessageParser
import rpsbot.rps.model.{Paper, Rock, Scissors}

import scala.util.Try

object MessageParserEN extends UserMessageParser {
  override def parse(in: String): UserMessage = in.toLowerCase.trim match {
    case r"/?help"  => Help
    case r"/?start" => Help

    case r"/?join\s+$lobbyId@(\d+)" =>
      JoinLobby(Try(lobbyId.asInstanceOf[String].toInt).get) // TODO is it possibly to get rid of the type cast to string?
    case r"/?rps\s+$lobbyId@(\d+)" =>
      JoinLobby(Try(lobbyId.asInstanceOf[String].toInt).get) // TODO is it possibly to get rid of the type cast to string?

    case r"/?play" => CreateLobby
    case r"/?rps"  => CreateLobby

    case r"/?r"    => Play(Rock)
    case r"/?rock" => Play(Rock)

    case r"/?p"     => Play(Paper)
    case r"/?paper" => Play(Paper)

    case r"/?s"        => Play(Scissors)
    case r"/?scissors" => Play(Scissors)

    case _ => Unknown
  }
}
