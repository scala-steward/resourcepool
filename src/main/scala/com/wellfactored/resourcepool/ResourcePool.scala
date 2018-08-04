package com.wellfactored.resourcepool

import cats.effect.{Bracket, Concurrent, Timer}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import fs2.async.mutable.Queue

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

case object TimeoutException extends Exception("operation timed out")

trait ResourcePool[F[_], T] {
  /**
    *
    * @param f       the function to run once a resource is available
    * @param timeout If the total time to obtain the resource and run the function exceeds this value then
    *                `runWithResource` will terminate with a `TimeoutException`
    */
  def runWithResource[A](f: T => F[A])(timeout: FiniteDuration): F[A]
}

object ResourcePool {
  def withResources[F[_] : Concurrent : Timer, T](resources: List[T]): F[ResourcePool[F, T]] = {
    for {
      q <- Queue.unbounded[F, T]
      _ <- resources.traverse(q.enqueue1)
    } yield new ResourcePool[F, T] {
      override def runWithResource[A](f: T => F[A])(timeout: FiniteDuration): F[A] = {
        val timer: F[A] = Timer[F].sleep(timeout) >> (throw TimeoutException)
        val op = Bracket[F, Throwable].bracket(q.dequeue1)(f)(q.enqueue1)

        Concurrent[F].race(op, timer).map {
          case Left(a) => a

          // The timer will never return an a, but need to match it for completeness
          case Right(a) => a
        }
      }
    }
  }
}

