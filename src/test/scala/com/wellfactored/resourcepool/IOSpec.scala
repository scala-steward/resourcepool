package com.wellfactored.resourcepool

import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext

trait IOSpec {
  implicit def timer(implicit ec: ExecutionContext): Timer[IO] = IO.timer(ec)
  implicit def contextShift(implicit ec: ExecutionContext): ContextShift[IO] = IO.contextShift(ec)
}
