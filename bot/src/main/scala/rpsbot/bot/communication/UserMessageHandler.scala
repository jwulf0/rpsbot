package rpsbot.bot.communication

import canoe.api.{TelegramClient, _}
import canoe.models.messages.TextMessage
import canoe.models.outgoing.TextContent
import canoe.models.{Chat, PrivateChat, User}
import cats.effect.{Concurrent, Sync}
import cats.implicits._
import cats.Monad
import rpsbot.bot.communication.model._
import rpsbot.bot.communication.operations.FeedbackTranslator
import rpsbot.bot.concurrentaccess.KeyGuard
import rpsbot.bot.model._
import rpsbot.bot.persistence.Storage
import rpsbot.rps.model.{Match, Move, ScoreEvent, Player1 => RpsPlayer1, Player2 => RpsPlayer2}
import rpsbot.rps.{operations => rpsoperations}

import scala.util.Random

/**
  * The message handlers handle-method contains the main logic of the RPS bot.
  */
class UserMessageHandler[F[_]: TelegramClient: Sync](storage: Storage[F], keyGuard: KeyGuard[F, Int], feedbackToMessage: FeedbackTranslator) {
  private val syncF = implicitly[Sync[F]]

  /**
    *  Will be applied in the calls to chat.send(), which expects [[canoe.models.outgoing.MessageContent]], one of which
    *  is [[canoe.models.outgoing.TextContent]].
    */
  implicit def generateMessageToUser(f: Feedback): TextContent = feedbackToMessage(f)

  /**
    * Pattern matches an (already parsed) [[UserMessage]] (with context) and executes the appropriate action(s) for it.
    */
  def handle(msg: UserMessageWithContext): F[Unit] = {
    val UserMessageWithContext(userMessage, tgMessage, user) = msg
    val chat                                                 = tgMessage.chat

    userMessage match {
      case Unknown => chat.send(MessageNotParsed).void
      case Help    => chat.send(HelpText).void

      case CreateLobby =>
        storage
          .findLobbyOfUser(user.id)
          .flatMap {
            case Some(l) => chat.send(AlreadyInLobby(l.id)).void
            case None    => createLobby(user, chat)
          }

      case JoinLobby(joinLobbyId) =>
        withGuardedUserLobby(
          user.id, {
            case Some(userLobby) if userLobby.id == joinLobbyId => chat.send(AlreadyInSameLobby).void
            case Some(userLobby) if userLobby.m.isDefined       => chat.send(AlreadyInMatch).void
            case other =>
              withLobbyGuard(
                joinLobbyId, {
                  case None                    => chat.send(LobbyNotFound(joinLobbyId)).void
                  case Some(l) if l.hasStarted => chat.send(LobbyFull(joinLobbyId)).void
                  case Some(l)                 => joinLobby(l, user, tgMessage, other)
                }
              )
          }
        )

      case Play(move) =>
        withGuardedUserLobby(
          user.id, {
            case None                     => chat.send(NotInAnyLobby).void
            case Some(l) if !l.hasStarted => chat.send(NoOpponentInLobby).void
            case Some(Lobby(_, _, _, _, Some(lastMatchUpdate))) if lastMatchUpdate > tgMessage.date => // ignore outdated messages
              syncF.unit
            case Some(l @ Lobby(_, p1, Some(p2), Some(m), _)) =>
              val (player: Player, moveBy: PlayerInMatch) = if (p1.userId == user.id) (p1, Player1) else (p2, Player2)

              storage.updatePlayer(l.id, player.copy(latestChatId = tgMessage.chat.id), moveBy) >>
                makeMoveOnMatch(l, p1, p2, m, move, moveBy, tgMessage)
          }
        )
    }
  }

  /**
    * Executes the given action with a guard on the lobby ID, regardless if the lobby exists or not. The lobby is
    * queried after the guard is acquired, so action can operate on it safely and must not acquire a guard for the id
    * (that would lead to a deadlock probably).
    */
  private def withLobbyGuard[A](lobbyId: Int, action: Option[Lobby] => F[A]): F[A] =
    keyGuard.withPermit(lobbyId, for {
      maybeLobby <- storage.findLobby(lobbyId)
      res        <- action(maybeLobby)
    } yield res)

  /**
    * Checks if the user currently has a lobby; if so, obtains a guard on that lobby and calls action with the guarded
    * lobby as parameter. Otherwise calls action with None as parameter and without acquiring any guards.
    */
  private def withGuardedUserLobby[A](userId: Int, action: Option[Lobby] => F[A]): F[A] =
    storage.findLobbyOfUser(userId).flatMap {
      case None => action(None)
      case Some(l) =>
        keyGuard.withPermit(
          l.id,
          for {
            lobby  <- storage.findLobby(l.id) // fetch the lobby again when guarded - it might have been modified or deleted in the meantime
            result <- action(lobby)
          } yield result
        )
    }

  /**
    * Helper method to keep handle() smaller.
    *
    * Assumes, it is valid for the user to create a lobby
    */
  private def createLobby(user: User, chat: Chat): F[Unit] =
    for {
      newId <- syncF.delay(Random.nextInt(8999) + 1000)
      lobby = Lobby(newId, Player(user.id, chat.id, user.firstName), None, None, None)
      _     <- keyGuard.withPermit(newId, storage.addLobby(lobby))
      _     <- chat.send(LobbyCreated(newId))
    } yield ()

  /**
    * Helper method to keep handle() smaller.
    *
    * Assumes, it is valid and safe for the user to join the lobby; also that it is valid and safe to delete the current user's lobby if it exists.
    */
  private def joinLobby(lobby: Lobby, user: User, tgMessage: TextMessage, maybeUserLobby: Option[Lobby]): F[Unit] = {
    lazy val beforeJoin: F[Unit] = maybeUserLobby match {
      case Some(userLobby) =>
        storage.deleteLobby(userLobby.id) >> tgMessage.chat.send(LobbyDeleted(userLobby.id)).void
      case None => syncF.unit
    }

    for {
      _ <- beforeJoin
      _ <- storage.joinLobby(lobby.id, Player(user.id, tgMessage.chat.id, user.firstName))
      _ <- storage.updateMatch(lobby.id, Match(0, 0, None, None), tgMessage.date)
      _ <- {
        val feedback = MatchStarted(lobby.p1.name, user.firstName)
        List(PrivateChat(lobby.p1.latestChatId, None, None, None), tgMessage.chat).traverse(_.send(feedback)).void
      }
    } yield ()
  }

  /**
    * Helper method to keep handle() smaller.
    *
    * Assumes, it is valid that a move is made on this match
    */
  private def makeMoveOnMatch(l: Lobby, p1: Player, p2: Player, m: Match, move: Move, moveBy: PlayerInMatch, tgMessage: TextMessage): F[Unit] = {
    val (updatedMatch, maybeScoreEvent)             = rpsoperations.move(m, move, if (moveBy == Player1) RpsPlayer1 else RpsPlayer2)

    val storageUpdate: F[Unit] = if(updatedMatch.isOver) storage.deleteLobby(l.id) else storage.updateMatch(l.id, updatedMatch, tgMessage.date).void

    val feedbackAction: F[Unit] = maybeScoreEvent match {
      case Some(event) =>
        val otherPlayerChatId  = if (moveBy == Player1) p2.latestChatId else p1.latestChatId
        val feedback: Feedback = ScoreEventInMatch(event, p1.name, p2.name)
        val chats              = List(tgMessage.chat, PrivateChat(otherPlayerChatId, None, None, None))
        chats.traverse(_.send(feedback)).void
      case None => syncF.unit
    }

    storageUpdate >> feedbackAction
  }
}

object UserMessageHandler {
  def create[F[_]: TelegramClient: Monad: Concurrent: Sync](storage: Storage[F], feedbackToMessage: FeedbackTranslator): F[UserMessageHandler[F]] =
    for {
      keyGuard <- KeyGuard.create[F, Int]
    } yield new UserMessageHandler[F](storage, keyGuard, feedbackToMessage)
}
