package rpsbot.rps

import rpsbot.rps.model._
import rpsbot.rps.model.implicits._

object operations {
  def init: Match = Match(0, 0, None, None)

  def move(in: Match, move: Move, player: PlayerInMatch): (Match, Option[ScoreEvent]) = {
    val matchWithMove = player match {
      case Player1 => in.copy(p1Move = Some(move))
      case _       => in.copy(p2Move = Some(move))
    }
    afterMove(matchWithMove)
  }

  private def afterMove(m: Match): (Match, Option[ScoreEvent]) = m match {
    case Match(s1, s2, Some(m1), Some(m2)) =>
      val (d1, d2) = scoreDeltas(m1, m2)
      val newMatchState = Match(s1 + d1, s2 + d2, None, None)
      (
        newMatchState,
        Some(ScoreEvent(s1 + d1, s2 + d2, m1, m2))
      )
    case _ => (m, None)
  }

  private def scoreDeltas(m1: Move, m2: Move): (Int, Int) =
    if (m1.beats(m2)) (1, 0)
    else if (m2.beats(m1)) (0, 1)
    else (0, 0)
}
