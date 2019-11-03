package rpsbot.bot.communication

import canoe.models.User
import canoe.models.messages.TextMessage
import rpsbot.rps.model.{Move, ScoreEvent}

object model {
  sealed trait UserMessage // messages from the user
  case object Unknown           extends UserMessage
  case object Help              extends UserMessage
  case object CreateLobby       extends UserMessage
  case class JoinLobby(id: Int) extends UserMessage
  case class Play(move: Move)   extends UserMessage

  sealed trait Feedback // messages sent to the user
  case object MessageNotParsed                                                    extends Feedback
  case object HelpText                                                            extends Feedback
  case object AlreadyInMatch                                                      extends Feedback
  case class AlreadyInLobby(id: Int)                                              extends Feedback
  case object AlreadyInSameLobby                                                  extends Feedback
  case class LobbyCreated(id: Int)                                                extends Feedback
  case class LobbyNotFound(id: Int)                                               extends Feedback
  case class LobbyFull(id: Int)                                                   extends Feedback
  case class LobbyDeleted(id: Int)                                                extends Feedback
  case class MatchStarted(p1Name: String, p2Name: String)                         extends Feedback
  case object NotInAnyLobby                                                       extends Feedback
  case object NoOpponentInLobby                                                   extends Feedback
  case class ScoreEventInMatch(event: ScoreEvent, p1Name: String, p2Name: String) extends Feedback // TODO maybe distinguish between text to p1 and text to p2?

  case class UserMessageWithContext(parsed: UserMessage, original: TextMessage, tgUser: User)
}
