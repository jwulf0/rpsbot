package rpsbot

import canoe.models.ParseMode
import canoe.models.outgoing.TextContent
import cats.Show
import cats.implicits._
import rpsbot.bot.communication.model
import rpsbot.bot.communication.model._
import rpsbot.bot.communication.operations.FeedbackTranslator
import rpsbot.rps.model._

object FeedbackEN extends FeedbackTranslator {
  implicit object ShowMove extends Show[Move] {
    override def show(t: Move): String = t match {
      case Rock     => "Rock"
      case Paper    => "Paper"
      case Scissors => "Scissors"
    }
  }

  // using *bold* in Telegram-supported markdown
  private val helpText = """
                           |*Without Game*
                           |Send "/rps" or "/play" to create a new game.
                           |Send "/rps 123" oder "/join 123" to join the game lobby 123.
                           |*In Game*
                           |Send "/rock" or "/r" to play Rock.
                           |Send "/paper" or "/p" to play Paper.
                           |Send "/scissors" or "/s" to play Scissors.
                           |
                           |(Note that you can omit the "/" for any command.)
                         """.stripMargin

  private def scoreEventToText(event: ScoreEvent, p1Name: String, p2Name: String): String = {
    val scoring = show"$p1Name plays ${event.p1Move}. $p2Name plays ${event.p2Move}. ${event.p1Score}:${event.p2Score}."
    val winner =
      if (event.p1Score == 3) s" $p1Name wins."
      else if (event.p2Score == 3) s" $p2Name wins."
      else ""
    scoring + winner
  }

  override def apply(f: model.Feedback): TextContent = f match {
    case MessageNotParsed                         => TextContent("""I don't understand what you mean. Send "/help" for... help.""")
    case HelpText                                 => TextContent(helpText, Some(ParseMode.Markdown))
    case AlreadyInLobby(id)                       => TextContent(s"You are already in a game lobby ($id).")
    case AlreadyInSameLobby                       => TextContent("You are already in this game.")
    case LobbyCreated(id)                         => TextContent(s"""Lobby created. Another player can now join your game with "/rps $id".""")
    case AlreadyInMatch                           => TextContent(s"You are already in a game.")
    case LobbyNotFound(id)                        => TextContent(s"No game lobby with ID $id found.")
    case LobbyFull(id)                            => TextContent(s"The game ($id) has already started.")
    case LobbyDeleted(id)                         => TextContent(s"You have left your game lobby ($id).")
    case MatchStarted(p1Name, p2Name)             => TextContent(s"It's on: $p1Name vs $p2Name. Fight with /rock, /paper, /scissors.")
    case NotInAnyLobby                            => TextContent("You're not in any game lobby.")
    case NoOpponentInLobby                        => TextContent("No second player has joined your game lobby yet.")
    case ScoreEventInMatch(event, p1Name, p2Name) => TextContent(scoreEventToText(event, p1Name, p2Name))
  }
}
