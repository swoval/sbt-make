package com.swoval.make.impl

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

import sbt.nio.{ FileChanges, FileStamp }

import scala.collection.immutable.VectorBuilder

private[make] object DiffStamps {
  // copied from sbt.nio.Settings.changedFiles
  def apply(previous: Seq[(Path, FileStamp)], current: Seq[(Path, FileStamp)]): FileChanges = {
    val createdBuilder = new VectorBuilder[Path]
    val deletedBuilder = new VectorBuilder[Path]
    val modifiedBuilder = new VectorBuilder[Path]
    val unmodifiedBuilder = new VectorBuilder[Path]
    val seen = ConcurrentHashMap.newKeySet[Path]
    val prevMap = new ConcurrentHashMap[Path, FileStamp]()
    previous.foreach { case (k, v) => prevMap.put(k, v); () }
    current.foreach {
      case (path, currentStamp) =>
        if (seen.add(path)) {
          prevMap.remove(path) match {
            case null => createdBuilder += path
            case old  => (if (old != currentStamp) modifiedBuilder else unmodifiedBuilder) += path
          }
        }
    }
    prevMap.forEach((p, _) => deletedBuilder += p)
    val unmodified = unmodifiedBuilder.result()
    val deleted = deletedBuilder.result()
    val created = createdBuilder.result()
    val modified = modifiedBuilder.result()
    if (created.isEmpty && deleted.isEmpty && modified.isEmpty) {
      FileChanges.unmodified(unmodifiedBuilder.result)
    } else {
      FileChanges(created, deleted, modified, unmodified)
    }
  }
}
