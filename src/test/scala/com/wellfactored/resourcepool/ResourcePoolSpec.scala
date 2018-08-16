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
      val iop = ResourcePool.of[IO, String](List(expected), (t, _) => IO.pure(t))

      val io = iop.flatMap { pool =>
        pool.runWithResource(s => IO(s))(100 milliseconds)
      }

      io.unsafeRunSync() shouldBe expected
    }
  }

  "A function that takes too long" - {
    "result in a TimeoutException" in {
      val expected = "expected string"
      val iop = ResourcePool.of[IO, String](List(expected), (t, _) => IO.pure(t))

      val io = iop.flatMap { pool =>
        pool.runWithResource(s => IO.sleep(200 milliseconds))(100 milliseconds)
      }

      io.attempt.unsafeRunSync().left.value shouldBe TimeoutException
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
      ResourcePool.of[IO, String](resources, (t, _) => IO.pure(t)).flatMap { pool =>
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
