package com.wellfactored.resourcepool

import cats.effect.{Bracket, Concurrent}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Stream
import fs2.async.mutable.Queue

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

trait ResourcePool[F[_], T] {
  def runWithResource[A](f: T => F[A])(timeout: FiniteDuration): F[A]
}

object ResourcePool {
  def withResources[F[_] : Concurrent, T](resources: Seq[T]): F[ResourcePool[F, T]] = {
    val ioq: F[Queue[F, T]] = Queue.unbounded[F, T]

    for {
      q <- ioq
      _ = Stream(resources: _*).to(q.enqueue)
    } yield new ResourcePool[F, T] {
      override def runWithResource[A](f: T => F[A])(timeout: FiniteDuration): F[A] = for {
        pool <- ioq
        a <- Bracket[F, Throwable].bracket(pool.dequeue1)(f)(pool.enqueue1)
      } yield a
    }
  }
}

