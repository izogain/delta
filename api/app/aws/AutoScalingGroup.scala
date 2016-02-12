package io.flow.delta.aws

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model._

import sun.misc.BASE64Encoder

import collection.JavaConverters._

object AutoScalingGroup extends Settings {
  lazy val client = new AmazonAutoScalingClient()
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
          .withAssociatePublicIpAddress(false)
          .withIamInstanceProfile(launchConfigIamInstanceProfile)
          .withBlockDeviceMappings(launchConfigBlockDeviceMappings)
          .withSecurityGroups(securityGroups.asJava)
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
          .withVPCZoneIdentifier(subnets.mkString(","))
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
    return Seq(
      """#!/bin/bash""",
      """echo 'OPTIONS="-e env=production"' > /etc/sysconfig/docker""",
      s"""echo 'ECS_CLUSTER=${EC2ContainerService.getClusterName(id)}' >> /etc/ecs/ecs.config""",
      """echo 'ECS_ENGINE_AUTH_TYPE=dockercfg' >> /etc/ecs/ecs.config""",
      """echo 'ECS_ENGINE_AUTH_DATA={"https://index.docker.io/v1/":{"auth":"Zmxvd2F3c2RvY2tlcjozNGRoNTlKMzlvSUdvUDRFbDlqeA==","email":"flow-aws-docker@flow.io"}}' >> /etc/ecs/ecs.config"""
    ).mkString("\n")
  }

}
