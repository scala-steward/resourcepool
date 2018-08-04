package com.wellfactored.resourcepool

import cats.effect.IO
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ResourcePoolTest extends WordSpecLike with Matchers with EitherValues {

  "A simple function" should {
    "return the right string" in {
      val expected = "expected string"
      val iop = ResourcePool.withResources[IO, String](List(expected))

      val io = iop.flatMap { pool =>
        pool.runWithResource(s => IO(s))(100 milliseconds)
      }

      io.unsafeRunSync() shouldBe expected
    }
  }

  "A function that takes too long" should {
    "result in a TimeoutException" in {
      val expected = "expected string"
      val iop = ResourcePool.withResources[IO, String](List(expected))

      val io = iop.flatMap { pool =>
        pool.runWithResource(s => IO.sleep(200 milliseconds))(100 milliseconds)
      }

      io.attempt.unsafeRunSync().left.value shouldBe TimeoutException
    }
  }

  "when two functions that take 75ms" should {
    def runTwoFunctions(resources: List[String]) = ResourcePool.withResources[IO, String](resources).flatMap { pool =>
      for {
        a <- pool.runWithResource(_ => IO.sleep(75 milliseconds))(100 milliseconds).start
        b <- pool.runWithResource(_ => IO.sleep(75 milliseconds))(100 milliseconds).start
      } yield (a.join, b.join)
    }

    "are run with a timeout of 100ms and only one resource then the second should fail" in {
      val (r1, r2) = runTwoFunctions(List("a")).unsafeRunSync()

      // The first function should succeed
      r1.attempt.unsafeRunSync() shouldBe a[Right[_, _]]

      // but the second should time out because the total time of the function (75ms) plus the time waiting for
      // the resource to become available (another 75ms) exceeds the timeout of 100ms
      r2.attempt.unsafeRunSync().left.value shouldBe TimeoutException
    }

    "are run with a timeout of 100ms and two resources then the both should succeed" in {
      val (r1, r2) = runTwoFunctions(List("a", "b")).unsafeRunSync()

      r1.attempt.unsafeRunSync() shouldBe a[Right[_, _]]
      r2.attempt.unsafeRunSync() shouldBe a[Right[_, _]]
    }
  }
}
