package com.wellfactored.resourcepool

import cats.Parallel
import cats.effect.IO
import cats.implicits._
import cats.temp.par.Par
import org.scalatest.{EitherValues, FreeSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ResourcePoolSpec extends FreeSpecLike with Matchers with EitherValues {

  "A simple function" - {
    "return the right string" in {
      val expected = "expected string"
      val iop = ResourcePool.of[IO, String](List(expected))

      val io = iop.flatMap { pool =>
        pool.runWithResource(s => IO(s))(100 milliseconds)
      }

      io.unsafeRunSync() shouldBe expected
    }
  }

  "A function that takes too long" - {
    "result in a TimeoutException" in {
      val expected = "expected string"
      val iop = ResourcePool.of[IO, String](List(expected))

      val io = iop.flatMap { pool =>
        pool.runWithResource(s => IO.sleep(200 milliseconds))(100 milliseconds)
      }

      io.attempt.unsafeRunSync().left.value shouldBe TimeoutException
    }
  }

  "when two functions that take 75ms" - {
    def runTwoFunctions(resources: List[String]) = ResourcePool.of[IO, String](resources).flatMap { pool =>
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


  "Multiple function calls" - {
    // A mutable resource that all the function calls will modify.
    var flag: Boolean = false

    def f(s: String): IO[Unit] = for {
      _ <- IO.suspend {
        if (flag) IO.raiseError(new Exception("flag is set")) else IO {
          flag = true
        }
      }
      _ <- IO.sleep(1 millis)
      _ <- IO.suspend {
        if (!flag) IO.raiseError(new Exception("flag is not set")) else IO {
          flag = false
        }
      }
    } yield ()

    val par = implicitly[Par[IO]]

    def runWithResources(resources: List[String]): List[Either[Throwable, Unit]] = {
      ResourcePool.of[IO, String](resources).flatMap { pool =>
        implicit val parallel: Parallel[IO, par.ParAux] = par.parallel

        // Run the function calls in parallel so that if there are any conflicting accesses to the flag they will show up
        (1 to 100).toList.parTraverse { _ => pool.runWithResource[Unit](f)(100 seconds).attempt }
      }.unsafeRunSync()
    }

    "against a single pooled resource should run without conflicting access to the shared state" in {
      runWithResources(List("a")).foreach(_ shouldBe a[Right[_, _]])
    }

    "against two pooled resources should end up with conflicting access to the shared state" in {
      runWithResources(List("a", "b")).exists(_.isLeft) shouldBe true
    }
  }
}
