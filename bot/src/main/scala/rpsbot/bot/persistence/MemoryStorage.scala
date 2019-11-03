package rpsbot.bot.persistence
import cats.Functor
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.SignallingRef
import rpsbot.bot.model.{Lobby, Player, Player1, PlayerInMatch}
import rpsbot.rps.model.Match

class MemoryStorage[F[_]: Functor](repo: Ref[F, Set[Lobby]]) extends Storage[F] {
  override def findLobby(id: Int): F[Option[Lobby]] = repo.get.map(_.find(_.id == id))

  override def deleteLobby(id: Int): F[Unit] = repo.update(_.filterNot(_.id == id))

  override def findLobbyOfUser(userId: Int): F[Option[Lobby]] =
    repo.get.map(_.find(lobby => lobby.p1.userId == userId || lobby.p2.map(_.userId).contains(userId)))

  override def addLobby(lobby: Lobby): F[Lobby] = repo.modify(lobbies => (lobbies + lobby, lobby))

  override def joinLobby(lobbyId: Int, user: Player): F[Lobby] =
    findAndModLobby(lobbyId, _.copy(p2 = Some(user)))

  override def updatePlayer(lobbyId: Int, user: Player, whichPlayer: PlayerInMatch): F[Lobby] =
    if (whichPlayer == Player1) findAndModLobby(lobbyId, _.copy(p1 = user))
    else findAndModLobby(lobbyId, _.copy(p2 = Some(user)))

  override def updateMatch(lobbyId: Int, updatedMatch: Match, timestamp: Int): F[Lobby] =
    findAndModLobby(lobbyId, _.copy(m = Some(updatedMatch)))

  private def findAndModLobby[A](lobbyId: Int, mod: Lobby => Lobby) =
    repo
      .modify[Either[String, Lobby]](lobbies =>
        lobbies.find(_.id == lobbyId) match {
          case None => (lobbies, Left("Lobby not found!"))
          case Some(lobby) =>
            val updatedLobby: Lobby        = mod(lobby)
            val updatedLobbies: Set[Lobby] = lobbies.filterNot(_.id == lobbyId) + updatedLobby
            (updatedLobbies, Right(updatedLobby))
      })
      .map(_.right.get) // ignore errors for now
}

object MemoryStorage {
  def build[F[_]: Concurrent](initialRepo: Set[Lobby] = Set()): F[MemoryStorage[F]] = {
    SignallingRef.apply[F, Set[Lobby]](initialRepo) map (repo => new MemoryStorage(repo))
  }
}
