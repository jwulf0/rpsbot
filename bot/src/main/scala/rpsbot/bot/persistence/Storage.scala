package rpsbot.bot.persistence

import rpsbot.bot.model.{Lobby, Player, PlayerInMatch}
import rpsbot.rps.model.Match

/**
  *
  * To run the RPS-Bot, these storage operations are required
  *
  * TODO Reflect the possibility of errors during runtime in the method signatures of this trait and, subsequently, in the implementations
  *
  * @tparam F Effect type
  */
trait Storage[F[_]] {

  def findLobby(id: Int): F[Option[Lobby]]
  def deleteLobby(id: Int): F[Unit]
  def findLobbyOfUser(userId: Int): F[Option[Lobby]]
  def addLobby(lobby: Lobby): F[Lobby]
  def joinLobby(lobbyId: Int, user: Player): F[Lobby]
  def updatePlayer(lobbyId: Int, user: Player, whichPlayer: PlayerInMatch): F[Lobby]
  def updateMatch(lobbyId: Int, updatedMatch: Match, timestamp: Int): F[Lobby]
}
