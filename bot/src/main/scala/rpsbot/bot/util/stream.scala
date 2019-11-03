package rpsbot.bot.util

import cats.Applicative
import cats.effect.{Concurrent, Timer}
import fs2.Pipe

import scala.concurrent.duration.FiniteDuration

object stream {
  def retryPipe[F[_]: Timer: Concurrent, A](timeout: FiniteDuration, maxRetries: Int, errorCallback: Throwable => F[Unit]): Pipe[F, A, A] =
    inStream =>
      inStream
        .attempts(fs2.Stream(timeout).repeatN(maxRetries))
        .evalTap {
          case Left(t) => errorCallback(t)
          case _       => implicitly[Applicative[F]].unit
        }
        .collect {
          case Right(a) => a
      }
}
