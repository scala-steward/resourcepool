/*
 * Copyright 2018 Well-Factored Software Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wellfactored.resourcepool

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, Timer}
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

  /**
    * Close the pool:
    *  - Any resources currently in the pool will be removed from the pool and passed to the cleanup function
    *  - Any resources currently in use by a function will, when released, be passed to the cleanup function rather
    * than being returned to the pool
    *  - Any calls to `runWithResource` after a call to `close` will result in an error being raised
    *  - Any subsequent calls to `close` will result in an error being raised
    *
    * @return a `Fiber` that
    */
  def close(cleanup: T => F[Unit]): F[Unit]
}

object ResourcePool {
  /**
    * Build a pool that is populated with the provided resources
    */
  def of[F[_] : Concurrent : Timer, T](resources: List[T]): F[ResourcePool[F, T]] = {
    for {
      q <- Queue.unbounded[F, T]
      _ <- resources.traverse(q.enqueue1)
    } yield of(q)
  }

  /**
    * Create a ResourcePool backed by a Queue that has been initialised with a set of resources
    */
  private def of[T, F[_]](q: Queue[F, T])(implicit concF: Concurrent[F], timerF: Timer[F]): ResourcePool[F, T] =
    new QueueBackedPool(q)


  /**
    * This implementation uses an FS2 mutable Queue to manage the resources.
    */
  class QueueBackedPool[F[_], T](q: Queue[F, T])(implicit concF: Concurrent[F], timerF: Timer[F]) extends ResourcePool[F, T] {
    private val cleanupMVar: F[MVar[F, T => F[Unit]]] =
      MVar.empty[F, T => F[Unit]]

    private def isClosed: F[Boolean] =
      cleanupMVar.flatMap(_.isEmpty.map(!_))


    /**
      * Run the function with a resource from the pool (once one is available). If the pool is closed, though,
      * raise an error.
      */
    override def runWithResource[A](f: T => F[A])(timeout: FiniteDuration): F[A] =
      for {
        _ <- raiseErrorIfClosed
        a <- runTimed(f, timeout)
      } yield a

    /**
      * "Close" the pool. Any resources currently in the pool (i.e. not currently in-use by a running
      * function) will be immediately de-queued and run through the cleanup function.
      *
      * The state of the pool will change so that any further calls to `runWithResource` will result in
      * an error being raised to indicate the closed state of the pool, and resources that are subsequently
      * released from running functions will be cleaned up rather than returned to the queue.
      *
      * If 'close' is called a second time it will return an error to indicate that the pool is already closed.
      *
      * @param cleanup a function to run against each resource in the pool to clean them up
      * @return `F[Unit]` - cleanup of all the resources currently in the pool will be complete by the time `F`
      *         completes. It is not guaranteed that resources not currently in the pool will have been cleaned up,
      *         however.
      */
    override def close(cleanup: T => F[Unit]): F[Unit] = {
      for {
        _ <- raiseErrorIfClosed
        _ <- cleanupMVar.flatMap(_.put(cleanup))
        chunk <- q.dequeueBatch1(Int.MaxValue)
      } yield {
        chunk.map(cleanup)
        ()
      }
    }

    private def raiseErrorIfClosed: F[Unit] =
      for {
        closed <- isClosed
        _ <- if (closed) concF.raiseError(new Exception("Pool has been closed")) else concF.unit
      } yield ()

    /**
      * If the pool is not closed then return the resource to the queue, otherwise clean it up
      * by running the cleanup function on it and do not return it to the pool.
      */
    private def requeueOrCleanup(t: T): F[Unit] = for {
      closed <- isClosed
      _ <- if (closed) cleanupMVar.flatMap(_.take).map(cleanup => cleanup(t))
           else q.enqueue1(t)
    } yield ()

    /**
      * Race the function against a timer, ensuring proper acquisition and release
      * of a resource.
      */
    private def runTimed[A](f: T => F[A], timeout: FiniteDuration): F[A] = {
      val timer: F[A] = timerF.sleep(timeout) >> (throw TimeoutException)
      val op: F[A] = concF.bracket(q.dequeue1)(f)(requeueOrCleanup)

      concF.race(op, timer).map(_.merge)
    }
  }
}