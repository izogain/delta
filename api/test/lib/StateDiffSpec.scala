package io.flow.delta.api.lib

import io.flow.delta.v0.models.{State, Version}
import org.joda.time.DateTime
import org.specs2.mutable._

class StateDiffSpec extends Specification {

  def buildState(versions: Seq[Version]): State = {
    State(
      timestamp = new DateTime(),
      versions = versions
    )
  }

  "Nil" in {
    val actual = buildState(Nil)
    StateDiff.up(actual, actual) must beEqualTo(Nil)
    StateDiff.down(actual, actual) must beEqualTo(Nil)
  }

  "no change" in {
    val actual = buildState(Seq(Version("0.0.1", 2), Version("0.0.2", 3)))
    StateDiff.up(actual, actual) must beEqualTo(Nil)
    StateDiff.down(actual, actual) must beEqualTo(Nil)
  }

  "bring up one instance" in {
    val actual = buildState(Seq(Version("0.0.1", 2)))
    val desired = buildState(Seq(Version("0.0.1", 3)))
    StateDiff.up(actual, desired) must beEqualTo(Seq(StateDiff("0.0.1", 2, 3)))
    StateDiff.down(actual, desired) must beEqualTo(Nil)
  }

  "bring up one instance w/ multiple versions" in {
    val actual = buildState(Seq(Version("0.0.1", 2), Version("0.0.2", 2)))
    val desired = buildState(Seq(Version("0.0.1", 2), Version("0.0.2", 3), Version("0.0.3", 1)))
    StateDiff.up(actual, desired) must beEqualTo(Seq(StateDiff("0.0.2", 2, 3), StateDiff("0.0.3", 0, 1)))
    StateDiff.down(actual, desired) must beEqualTo(Nil)
  }

  "bring down one instance" in {
    val actual = buildState(Seq(Version("0.0.1", 2)))
    val desired = buildState(Seq(Version("0.0.1", 1)))
    StateDiff.up(actual, desired) must beEqualTo(Nil)
    StateDiff.down(actual, desired) must beEqualTo(Seq(StateDiff("0.0.1", 2, 1)))
  }

  "bring down one instance w/ multiple versions" in {
    val actual = buildState(Seq(Version("0.0.1", 2), Version("0.0.2", 2)))
    val desired = buildState(Seq(Version("0.0.1", 1), Version("0.0.2", 1)))
    StateDiff.up(actual, desired) must beEqualTo(Nil)
    StateDiff.down(actual, desired) must beEqualTo(Seq(StateDiff("0.0.1", 2, 1), StateDiff("0.0.2", 2, 1)))
  }

  "upgrade version" in {
    val actual = buildState(Seq(Version("0.0.1", 2)))
    val desired = buildState(Seq(Version("0.0.2", 2)))
    StateDiff.up(actual, desired) must beEqualTo(Seq(StateDiff("0.0.2", 0, 2)))
    StateDiff.down(actual, desired) must beEqualTo(Seq(StateDiff("0.0.1", 2, 0)))
  }

  "downgrade version" in {
    val actual = buildState(Seq(Version("0.0.2", 2)))
    val desired = buildState(Seq(Version("0.0.1", 2)))
    StateDiff.up(actual, desired) must beEqualTo(Seq(StateDiff("0.0.1", 0, 2)))
    StateDiff.down(actual, desired) must beEqualTo(Seq(StateDiff("0.0.2", 2, 0)))
  }

}
