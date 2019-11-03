package rpsbot.bot.persistence

import cats.effect.Async
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder
import org.scanamo.error.DynamoReadError
import org.scanamo.syntax.{set, _}
import org.scanamo.{ScanamoCats, Table}
import org.scanamo.auto._
import rpsbot.bot.model.{Lobby, Player, Player1, PlayerInMatch}
import rpsbot.rps.model.Match

/**
  * A DynamoDB implementation of [[Storage]] using Scanamo.
  */
class DynamoStorage[F[_]](dbName: String, scanamoInterpreter: ScanamoCats[F]) extends Storage[F] {
  private val table = Table[Lobby](dbName)

  private def ignoreErrors[A](opResult: Either[DynamoReadError, A]): A = opResult.right.get

  override def findLobbyOfUser(userId: Int): F[Option[Lobby]] =
    scanamoInterpreter.exec(
      table
        .scan()
        .map(_.collect {
          case Right(l) if l.p1.userId == userId || l.p2.exists(_.userId == userId) => l
        }.headOption))

  override def addLobby(lobby: Lobby): F[Lobby] = scanamoInterpreter.exec(
    table.put(lobby).map(_ => lobby)
  )

  override def joinLobby(lobbyId: Int, user: Player): F[Lobby] = scanamoInterpreter.exec(
    table.update("id" -> lobbyId, set("p2" -> Some(user))).map(ignoreErrors)
  )

  override def updateMatch(lobbyId: Int, updatedMatch: Match, timestamp: Int): F[Lobby] =
    scanamoInterpreter.exec(
      table
        .update("id" -> lobbyId, set("m" -> Some(updatedMatch)) and set("lastMatchUpdate" -> Some(timestamp)))
        .map(ignoreErrors)
    )

  override def findLobby(id: Int): F[Option[Lobby]] = scanamoInterpreter.exec(
    table.get("id" -> id).map {
      case Some(Right(l)) => Some(l)
      case _              => None
    }
  )

  override def deleteLobby(id: Int): F[Unit] = scanamoInterpreter.exec(table.delete("id" -> id).map(_ => ()))

  override def updatePlayer(lobbyId: Int, user: Player, as: PlayerInMatch): F[Lobby] = {
    val playerKey = if (as == Player1) "p1" else "p2"
    scanamoInterpreter.exec(table.update("id" -> lobbyId, set(playerKey -> user)).map(ignoreErrors))
  }
}

object DynamoStorage {
  def build[F[_]: Async](tableName: String): DynamoStorage[F] = {
    val scanamo: ScanamoCats[F] = ScanamoCats(AmazonDynamoDBAsyncClientBuilder.defaultClient())
    new DynamoStorage[F](tableName, scanamo)
  }
}
