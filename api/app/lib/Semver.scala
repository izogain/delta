package io.flow.delta.api.lib

case class Semver(major: Int, minor: Int, micro: Int) extends Ordered[Semver] {

  val label = {
    Seq(major, minor, micro).mkString(".")
  }

  def next(): Semver = {
    Semver(major, minor, micro + 1)
  }

  def compare(other: Semver) = {
    if (major == other.major) {
      if (minor == other.minor) {
        micro.compare(other.micro)
      } else {
        minor.compare(other.minor)
      }
    } else {
      major.compare(other.major)
    }
  }

}

object Semver {

  private[this] val Pattern = """^(\d+)\.(\d+)\.(\d+)$""".r

  def parse(value: String): Option[Semver] = {
    value match {
      case Pattern(major, minor, micro) => Some(Semver(major.toInt, minor.toInt, micro.toInt))
      case _ => None
    }
  }

  def isSemver(value: String): Boolean = {
    value match {
      case Pattern(major, minor, micro) => true
      case _ => false
    }
  }
}

