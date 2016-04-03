package io.flow.delta.aws

case class InstanceType(launchConfigInstanceType: String, containerMemory: Int) extends Settings

object InstanceTypes {

  val T2Micro = InstanceType("t2.micro", 400)
  val T2Small = InstanceType("t2.small", 1500)
  val T2Medium = InstanceType("t2.medium", 3500)
  val T2Large = InstanceType("t2.large", 7500)

  val M4Large = InstanceType("m4.large", 7500)

}

