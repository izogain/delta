package aws

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model._

import sun.misc.BASE64Encoder

import collection.JavaConverters._

object AutoScalingGroup {

  lazy val client = new AmazonAutoScalingClient()

  lazy val encoder = new BASE64Encoder()

  // Stuff needed to set up related things
  // TODO: Make configurable
  lazy val asgHealthCheckGracePeriod = 300
  lazy val asgMinSize = 3
  lazy val asgMaxSize = 3
  lazy val asgDesiredSize = 3
  lazy val asgSubnets = "subnet-4a8ee313,subnet-7d2f1547,subnet-c99a0de2,subnet-dc2961ab"
  lazy val ec2KeyName = "mbryzek-key-pair-us-east"
  lazy val lcImageId = "ami-9886a0f2"
  lazy val lcInstanceType = "t2.micro"
  lazy val lcIamInstanceProfile = "ecsInstanceRole"
  lazy val lcSecurityGroups = Seq("sg-4aead833", "sg-abfaf7cf").asJava

  /**
  * Defined Values, probably make object vals somewhere?
  */
  lazy val lcBlockDeviceMappings = Seq(
    new BlockDeviceMapping()
      .withDeviceName("/dev/xvda")
      .withEbs(new Ebs()
        .withDeleteOnTermination(true)
        .withVolumeSize(8)
        .withVolumeType("gp2")
      ),
    new BlockDeviceMapping()
      .withDeviceName("/dev/xvdcz")
      .withEbs(new Ebs()
        .withDeleteOnTermination(true)
        .withEncrypted(false)
        .withVolumeSize(22)
        .withVolumeType("gp2")
      )
  ).asJava

  def createLaunchConfiguration(id: String): String = {
    val name = s"$id-api-ecs-launch-configuration"
    client.createLaunchConfiguration(
      new CreateLaunchConfigurationRequest()
        .withLaunchConfigurationName(name)
        .withAssociatePublicIpAddress(true)
        .withIamInstanceProfile(lcIamInstanceProfile)
        .withBlockDeviceMappings(lcBlockDeviceMappings)
        .withSecurityGroups(lcSecurityGroups)
        .withKeyName(ec2KeyName)
        .withImageId(lcImageId)
        .withInstanceType(lcInstanceType)
        .withUserData(encoder.encode(lcUserData(id).getBytes))
    )
    return name
  }

  def createAutoScalingGroup(id: String, launchConfigName: String, loadBalancerName: String): String = {
    val name = s"$id-api-ecs-auto-scaling-group"
    client.createAutoScalingGroup(
      new CreateAutoScalingGroupRequest()
        .withAutoScalingGroupName(name)
        .withLaunchConfigurationName(launchConfigName)
        .withLoadBalancerNames(Seq(loadBalancerName).asJava)
        .withVPCZoneIdentifier(asgSubnets)
        .withNewInstancesProtectedFromScaleIn(false)
        .withHealthCheckGracePeriod(asgHealthCheckGracePeriod)
        .withMinSize(asgMinSize)
        .withMaxSize(asgMaxSize)
        .withDesiredCapacity(asgDesiredSize)
    )
    return name
  }

  def lcUserData(id: String): String = {
    return Seq(
      """#!/bin/bash""",
      """echo 'OPTIONS="-e env=production"' > /etc/sysconfig/docker""",
      s"""echo 'ECS_CLUSTER=${EC2ContainerService.getClusterName(id)}' >> /etc/ecs/ecs.config""",
      """echo 'ECS_ENGINE_AUTH_TYPE=dockercfg' >> /etc/ecs/ecs.config""",
      """echo 'ECS_ENGINE_AUTH_DATA={"https://index.docker.io/v1/":{"auth":"Zmxvd2F3c2RvY2tlcjozNGRoNTlKMzlvSUdvUDRFbDlqeA==","email":"flow-aws-docker@flow.io"}}' >> /etc/ecs/ecs.config"""
    ).mkString("\n")
  }

}
