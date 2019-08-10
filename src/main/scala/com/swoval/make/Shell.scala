package com.swoval.make

import sbt.Logger

import scala.annotation.tailrec
import scala.collection.JavaConverters._

object Shell {
  def apply(args: String*): Unit = apply(None, args: _*)
  def apply(log: Option[Logger], args: String*): Unit = {
    log.foreach(_.debug(s"Running shell command: '${args.mkString(" ")}'"))
    val process = new java.lang.ProcessBuilder(args.flatMap(_.split("[ ]+")): _*).start()
    val is = process.getInputStream
    val es = process.getErrorStream
    val inputBytes = new java.util.Vector[Byte]()
    val errorBytes = new java.util.Vector[Byte]()
    val readerThread = new Thread("process-reader-thread") {
      setDaemon(true)
      start()
      @tailrec
      override def run(): Unit = {
        def drain(): Unit = {
          if (is.available > 0) {
            while (is.available > 0) {
              inputBytes.add((is.read() & 0xFF).toByte)
            }
            val lines =
              new String(Array(inputBytes.asScala: _*)).linesIterator.filter(_.trim.nonEmpty)
            lines.foreach(l => log.foreach(_.info(l)))
            inputBytes.clear()
          }
          if (es.available > 0) {
            while (es.available > 0) {
              errorBytes.add((es.read() & 0xFF).toByte)
            }
            val lines =
              new String(Array(errorBytes.asScala: _*)).linesIterator.filter(_.trim.nonEmpty)
            lines.foreach(l => log.foreach(_.error(l)))
            errorBytes.clear()
          }
        }
        if (process.isAlive) {
          drain()
          if (process.isAlive) Thread.sleep(2)
          run()
        } else {
          drain()
        }
      }
    }
    try readerThread.join()
    catch { case _: InterruptedException => }
    if (process.exitValue != 0) {
      val msg = s"Process returned non-zero exit code: ${process.exitValue}"
      throw new IllegalStateException(msg)
    }
    ()
  }
}
