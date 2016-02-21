package io.flow.delta.aws

import io.flow.play.util.DefaultConfig

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model._

import sun.misc.BASE64Encoder

import collection.JavaConverters._

case class AutoScalingGroup(
  eC2ContainerService: EC2ContainerService,
  dockerHubToken: String,
  dockerHubEmail: String
) extends Settings with Credentials {

  lazy val client = new AmazonAutoScalingClient(awsCredentials)
  lazy val encoder = new BASE64Encoder()

  /**
  * Defined Values, probably make object vals somewhere?
  */
  val launchConfigBlockDeviceMappings = Seq(
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

  def getLaunchConfigurationName(id: String) = s"$id-ecs-launch-configuration"

  def getAutoScalingGroupName(id: String) = s"$id-ecs-auto-scaling-group"

  def createLaunchConfiguration(id: String): String = {
    val name = getLaunchConfigurationName(id)
    try {
      client.createLaunchConfiguration(
        new CreateLaunchConfigurationRequest()
          .withLaunchConfigurationName(name)
          .withAssociatePublicIpAddress(true)
          .withIamInstanceProfile(launchConfigIamInstanceProfile)
          .withBlockDeviceMappings(launchConfigBlockDeviceMappings)
          .withSecurityGroups(lcSecurityGroups.asJava)
          .withKeyName(ec2KeyName)
          .withImageId(launchConfigImageId)
          .withInstanceType(launchConfigInstanceType)
          .withUserData(encoder.encode(lcUserData(id).getBytes))
      )
    } catch {
      case e: AlreadyExistsException => println(s"Launch Configuration '$name' already exists")
    }

    return name
  }

  def createAutoScalingGroup(id: String, launchConfigName: String, loadBalancerName: String): String = {
    val name = getAutoScalingGroupName(id)
    try {
      client.createAutoScalingGroup(
        new CreateAutoScalingGroupRequest()
          .withAutoScalingGroupName(name)
          .withLaunchConfigurationName(launchConfigName)
          .withLoadBalancerNames(Seq(loadBalancerName).asJava)
          .withVPCZoneIdentifier(asgSubnets.mkString(","))
          .withNewInstancesProtectedFromScaleIn(false)
          .withHealthCheckGracePeriod(asgHealthCheckGracePeriod)
          .withMinSize(asgMinSize)
          .withMaxSize(asgMaxSize)
          .withDesiredCapacity(asgDesiredSize)
      )
    } catch {
      case e: AlreadyExistsException => println(s"Launch Configuration '$name' already exists")
    }

    return name
  }

  def lcUserData(id: String): String = {
    val ecsClusterName = eC2ContainerService.getClusterName(id)

    return Seq(
      """#!/bin/bash""",
      """echo 'OPTIONS="-e env=production"' > /etc/sysconfig/docker""",
      s"""echo 'ECS_CLUSTER=${ecsClusterName}' >> /etc/ecs/ecs.config""",
      """echo 'ECS_ENGINE_AUTH_TYPE=dockercfg' >> /etc/ecs/ecs.config""",
      s"""echo 'ECS_ENGINE_AUTH_DATA={"https://index.docker.io/v1/":{"auth":"${dockerHubToken}","email":"${dockerHubEmail}"}}' >> /etc/ecs/ecs.config"""
    ).mkString("\n")
  }

}
