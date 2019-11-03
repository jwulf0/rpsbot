package rpsbot.rps

object model {
  sealed trait Move
  case object Rock     extends Move
  case object Paper    extends Move
  case object Scissors extends Move

  case class Match(p1Score: Int, p2Score: Int, p1Move: Option[Move], p2Move: Option[Move]) {
    def isOver: Boolean = p1Score >= 3 || p2Score >= 3
  }

  sealed trait PlayerInMatch
  case object Player1 extends PlayerInMatch
  case object Player2 extends PlayerInMatch

  case class ScoreEvent(p1Score: Int, p2Score: Int, p1Move: Move, p2Move: Move)

  object implicits {
    implicit class MoveOps(move: Move) {
      def beats(other: Move) = (move, other) match {
        case (Rock, Scissors)  => true
        case (Rock, _)         => false
        case (Scissors, Paper) => true
        case (Scissors, _)     => false
        case (Paper, Rock)     => true
        case (Paper, _)        => false
      }
    }

    implicit class MatchOps(m: Match) {
      def hasEnded: Boolean = m.p1Score > 2 || m.p2Score > 2
    }
  }
}
