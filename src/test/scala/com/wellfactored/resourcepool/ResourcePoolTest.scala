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

}
