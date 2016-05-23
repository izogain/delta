package io.flow.delta.aws

import io.flow.delta.config.v0.models.InstanceType

case class DefaultSettings(
  instanceType: InstanceType,
  override val containerMemory: Int,  // in MB
  override val portContainer: Int,
  override val portHost: Int
) extends Settings {

  override val launchConfigInstanceType = instanceType.toString
}

/**
* Single place to find all the AWS-related configuration settings.
* Make it easy to change (or fix) in one place.
**/
trait Settings {

  // SSL settings for ELB listeners
  val elbSslCertificateId = "arn:aws:acm:us-east-1:479720515435:certificate/6ed9a4a3-5d83-4cae-8506-ea0b379ae793"

  // Subnets for the load balancer and autoscaling group instances
  // Use these, per Kunal @ AWS
  // subnet-3538f243 = production-dmz-us-east-1a
  // subnet-6c9c7034 = production-dmz-us-east-1b
  // subnet-2338f255 = production-private-us-east-1a
  // subnet-719c7029 = production-private-us-east-1b
  val elbSubnets = Seq("subnet-3538f243", "subnet-6c9c7034")
  val asgSubnets = Seq("subnet-719c7029", "subnet-2338f255")

  // Security groups for the EC2 instances launch configuration and autoscaling group
  // sg-b7b764cf = production-ecs-default
  // sg-55bb682d = production-elb-default
  val lcSecurityGroups = Seq("sg-b7b764cf")
  val elbSecurityGroups = Seq("sg-55bb682d")

  // Length of time in seconds after a new Amazon EC2 instance comes into service that Auto Scaling starts checking its health
  val asgHealthCheckGracePeriod = 300

  // The minimum size of the Auto Scaling group
  val asgMinSize = 2

  // The maximum size of the Auto Scaling group
  val asgMaxSize = 16

  // The number of Amazon EC2 instances that should be running in the autoscaling group
  val asgDesiredSize = 4

  // Keypair name used to SSH into EC2 instances created by the autoscaling group
  val ec2KeyName = "flow-admin"

  /**
    * TODO:
    * Figure out how to update existing Launch configurations (or create new ones),
    * associate to the autoscaling group, and gracefully update existing instances.
    *
    */
  // Launch configuration image ID. AMI instance is: Amazon Linux AMI for ECS (2016.03)
  val launchConfigImageId = "ami-1d48ab70"

  // Role for the new launch configuration
  val launchConfigIamInstanceProfile = "ecsInstanceRole"

  // EC2 service role
  val serviceRole = "ecsServiceRole"

  // When a new service is created, explicitly set the desired count to 0
  val createServiceDesiredCount = 0

  // Launch configuration EC2 instance type, ex. t2.micro
  val launchConfigInstanceType: String

  // MB of memory
  val containerMemory: Int

  val portContainer: Int

  val portHost: Int
}
