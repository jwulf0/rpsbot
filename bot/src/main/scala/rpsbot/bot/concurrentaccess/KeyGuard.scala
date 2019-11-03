package rpsbot.bot.concurrentaccess

import cats.effect.Concurrent
import cats.effect.concurrent.{Ref, Semaphore}
import cats.implicits._

/**
  * A helper class to juggle [[Semaphore]]s so that for any given key of type K, only one semaphore exists and can be
  * obtained to ensure transactional operations on resources represented by the key.
  *
  * Should be instantiated via the companion object's create-method.
  *
  * Note that an application using this tool will of course not be horizontally scalable since the semaphores live
  * in-memory.
  */
class KeyGuard[F[_]: Concurrent, K](keyGuardsRef: Ref[F, Map[K, Semaphore[F]]], outerGuard: Semaphore[F]) {

  /**
    * Executes the given action with a permit for the guard (possibly newly created) for the given key.
    */
  def withPermit[A](key: K, action: F[A]): F[A] =
    for {
      guardForKey <- getGuard(key)
      result      <- guardForKey.withPermit(action)
    } yield result

  /**
    * Obtains a guard/semaphore for the given key. If necessary, creates it beforehand.
    * It is wrapped in a permit of the "outer guard" so only one key-guard can be obtained at a time.
    */
  def getGuard(key: K): F[Semaphore[F]] =
    outerGuard.withPermit(for {
      guards <- keyGuardsRef.get
      guardForKey <- guards
                      .get(key)
                      .map(implicitly[Concurrent[F]].point(_))
                      .getOrElse(
                        Semaphore.apply(1).flatMap(sem => keyGuardsRef.modify(guards => (guards.updated(key, sem), sem)))
                      )
    } yield guardForKey)
}

object KeyGuard {
  def create[F[_]: Concurrent, K]: F[KeyGuard[F, K]] =
    for {
      keyGuardsRef <- Ref.of[F, Map[K, Semaphore[F]]](Map())
      outerGuard   <- Semaphore.apply[F](1)
    } yield new KeyGuard[F, K](keyGuardsRef, outerGuard)
}
