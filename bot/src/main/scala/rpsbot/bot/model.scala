package rpsbot.bot

import rpsbot.rps.model.Match

object model {
  case class Player(userId: Int, latestChatId: Long, name: String)
  case class Lobby(id: Int, p1: Player, p2: Option[Player], m: Option[Match], lastMatchUpdate: Option[Int]) {
    def hasStarted = p2.isDefined || m.isDefined
  }

  // This distinction is required at some points
  sealed trait PlayerInMatch
  case object Player1 extends PlayerInMatch
  case object Player2 extends PlayerInMatch
}
