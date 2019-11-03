package rpsbot

import canoe.models.ParseMode
import canoe.models.outgoing.TextContent
import cats.Show
import cats.implicits._
import rpsbot.bot.communication.model
import rpsbot.bot.communication.model._
import rpsbot.bot.communication.operations.FeedbackTranslator
import rpsbot.rps.model._

object FeedbackDE extends FeedbackTranslator {
  implicit object ShowMove extends Show[Move] {
    override def show(t: Move): String = t match {
      case Rock     => "Stein"
      case Paper    => "Papier"
      case Scissors => "Schere"
    }
  }

  // using *bold* in Telegram-supported markdown
  private val helpText = """
                           |*Ohne Spiel*
                           |Schreibe "schnick" oder "/schnick", um ein Spiel zu eröffnen.
                           |Schreibe "schnick 123" oder "/schnick 123" um dem Spiel 123 beizutreten.
                           |*Im Spiel*
                           |Schreibe "schere" oder "/schere", um Schere zu spielen.
                           |Schreibe "stein" oder "/stein", um Papier zu spielen.
                           |Schreibe "papier" oder "/papier", um Papier zu spielen.
                         """.stripMargin

  private def scoreEventToText(event: ScoreEvent, p1Name: String, p2Name: String): String = {
    val scoring = show"$p1Name spielt ${event.p1Move}. $p2Name spielt ${event.p2Move}. ${event.p1Score}:${event.p2Score}."
    val winner =
      if (event.p1Score == 3) s" $p1Name gewinnt."
      else if (event.p2Score == 3) s" $p2Name gewinnt."
      else ""
    scoring + winner
  }

  override def apply(f: model.Feedback): TextContent = f match {
    case MessageNotParsed                         => TextContent("Ich habe dich nicht verstanden. Infos unter /help.")
    case HelpText                                 => TextContent(helpText, Some(ParseMode.Markdown))
    case AlreadyInLobby(id)                       => TextContent(s"Du bist bereits in einer Lobby ($id).")
    case AlreadyInSameLobby                       => TextContent("Du bist bereits in diesem Spiel.")
    case LobbyCreated(id)                         => TextContent(s"Mit /schnick $id kann jemand deinem Spiel beitreten.")
    case AlreadyInMatch                           => TextContent("Du bist bereits in einem laufenden Spiel.")
    case LobbyNotFound(_)                         => TextContent("Keine Lobby mit der ID gefunden.")
    case LobbyFull(_)                             => TextContent("Das Spiel hat bereits angefangen.")
    case LobbyDeleted(id)                         => TextContent(s"Du hast dein Spiel (${id}) verlassen.")
    case MatchStarted(p1Name, p2Name)             => TextContent(s"Es spielen: $p1Name gegen $p2Name. Schnickt mit /stein, /papier, /schere.")
    case NotInAnyLobby                            => TextContent("Du bist in keinem Spiel.")
    case NoOpponentInLobby                        => TextContent("Es ist noch niemand deinem Spiel beigetreten.")
    case ScoreEventInMatch(event, p1Name, p2Name) => TextContent(scoreEventToText(event, p1Name, p2Name))
  }
}
