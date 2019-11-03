package rpsbot.bot.concurrentaccess

import cats.Parallel
import cats.effect.{Clock, ConcurrentEffect, Sync, Timer}
import cats.implicits._

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

object KeyGuardTestUtils {
  case class Msg(key: Char, payload: Int, duration: FiniteDuration)

  case class AnalyzedMsg(msg: Msg, timeStarted: Long, timeFinished: Long)

  def testRun[F[_]: ConcurrentEffect: Clock: Timer: Parallel](keyGuard: KeyGuard[F, Char], messages: List[Msg]): F[List[AnalyzedMsg]] = {
    lazy val ce: ConcurrentEffect[F] = implicitly[ConcurrentEffect[F]]
    lazy val clock: Clock[F]         = implicitly[Clock[F]]
    lazy val timer: Timer[F]         = implicitly[Timer[F]]
    lazy val sync: Sync[F]           = implicitly[Sync[F]]

    def processMessage(globalStart: Long)(msg: Msg): F[AnalyzedMsg] =
      keyGuard.withPermit(
        msg.key,
        for {
          timeStarted  <- clock.monotonic(MILLISECONDS)
          _            <- timer.sleep(msg.duration)
          timeFinished <- clock.monotonic(MILLISECONDS)
          anlyzedMsg   = AnalyzedMsg(msg, timeStarted - globalStart, timeFinished - globalStart)
          _            <- sync.delay(println(anlyzedMsg))
        } yield anlyzedMsg
      )

    clock.monotonic(MILLISECONDS).flatMap(globalStart => messages.parTraverse[F, AnalyzedMsg](processMessage(globalStart)))
  }
}
