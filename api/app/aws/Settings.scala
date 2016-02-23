package io.flow.delta.aws

/**
* Single place to find all the AWS-related configuration settings.
* Make it easy to change (or fix) in one place.
**/
trait Settings {

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
  val asgMinSize = 4

  // The maximum size of the Auto Scaling group
  val asgMaxSize = 4

  // The number of Amazon EC2 instances that should be running in the autoscaling group
  val asgDesiredSize = 4

  // Keypair name used to SSH into EC2 instances created by the autoscaling group
  val ec2KeyName = "flow-admin"

  // Launch configuration image ID. AMI instance is: Amazon Linux AMI for ECS
  val launchConfigImageId = "ami-9886a0f2"

  // Role for the new launch configuration
  val launchConfigIamInstanceProfile = "ecsInstanceRole"

  // Should this be higher? And if so, probably should use something other than t2.micro
  val containerMemory = 400

  // Launch configuration EC2 instance type. t2 micro for now, but probably change later or make configurable
  val launchConfigInstanceType = "t2.micro"

  // EC2 service role
  val serviceRole = "ecsServiceRole"

  // When a new service is created, explicitly set the desired count to 0, then scale up to 3
  val createServiceDesiredCount = 0

}
