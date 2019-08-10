package com.swoval.make.impl

import sbt.ConcurrentRestrictions.Tag

object Settings {
  val concurrency = Tag("make-concurrency")
}
