package rpsbot.bot.concurrentaccess

import cats.effect.{Clock, ContextShift, IO, Timer}
import cats.implicits._
import org.scalatest.{AsyncFlatSpec, Matchers}
import rpsbot.bot.concurrentaccess.KeyGuardTestUtils.{AnalyzedMsg, Msg}

import scala.concurrent.duration._

class KeyGuardTest extends AsyncFlatSpec with Matchers {
  implicit val timer: Timer[IO]     = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val clock: Clock[IO]     = Clock.create[IO]

  private lazy val testMessages = List(
    Msg('A', 1, 1 second),
    Msg('B', 1, 550 milliseconds),
    Msg('B', 2, 550 milliseconds),
    Msg('C', 1, 400 milliseconds),
    Msg('A', 2, 1 second),
    Msg('C', 2, 400 milliseconds),
    Msg('B', 3, 550 milliseconds)
  )

  "The default keyguard" should "delay guarded operations" in {
    val runRes: IO[Seq[AnalyzedMsg]] = for {
      guard    <- KeyGuard.create[IO, Char]
      analyzed <- KeyGuardTestUtils.testRun(guard, testMessages)
    } yield analyzed

    runRes.attempt.map {
      case Right(analyzed) =>
        analyzed.foreach { msg =>
          val othersInGroup = analyzed.filter(_.msg.key == msg.msg.key).filterNot(_ == msg)
          // Every other message in the group (i.e. with the same key) needs to be either started and finsished before
          // the message (if-case) or started and finished after the message (else case)
          othersInGroup.foreach(
            otherMsg =>
              if (otherMsg.timeStarted <= msg.timeStarted) otherMsg.timeFinished should be <= msg.timeStarted
              else {
                otherMsg.timeStarted should be >= msg.timeFinished
                otherMsg.timeFinished should be >= msg.timeFinished
            })
        }

        analyzed.length shouldBe testMessages.length

      case _ => fail("Error in test run")
    } unsafeToFuture
  }
}
