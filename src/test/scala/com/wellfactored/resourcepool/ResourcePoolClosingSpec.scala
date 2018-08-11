package com.wellfactored.resourcepool

import cats.effect.IO
import org.scalatest.{EitherValues, FreeSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global

class ResourcePoolClosingSpec extends FreeSpecLike with Matchers with EitherValues {
  "calling close" - {
    "should result in the cleanup function being called on each resource currently available in the pool" in {
      case class Resource(var cleanupCalled: Boolean = false)
      val cleanup: Resource => IO[Unit] = { r => r.cleanupCalled = true; IO.unit }
      val resources: List[Resource] = List.fill(5)(Resource())

      val test = for {
        pool <- ResourcePool.of[IO, Resource](resources)
        _ <- pool.close(cleanup)
      } yield resources

      test.unsafeRunSync().foreach(_.cleanupCalled shouldBe true)
    }
  }
}
