package io.flow.delta.aws

import io.flow.play.util.DefaultConfig

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model._

import play.api.Logger
import sun.misc.BASE64Encoder

import collection.JavaConverters._

case class AutoScalingGroup(
  settings: Settings,
  eC2ContainerService: EC2ContainerService,
  dockerHubToken: String,
  dockerHubEmail: String
) extends Credentials {

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
          .withAssociatePublicIpAddress(false)
          .withIamInstanceProfile(settings.launchConfigIamInstanceProfile)
          .withBlockDeviceMappings(launchConfigBlockDeviceMappings)
          .withSecurityGroups(settings.lcSecurityGroups.asJava)
          .withKeyName(settings.ec2KeyName)
          .withImageId(settings.launchConfigImageId)
          .withInstanceType(settings.launchConfigInstanceType)
          .withUserData(encoder.encode(lcUserData(id).getBytes))
      )
    } catch {
      case e: AlreadyExistsException => Logger.error(s"Launch Configuration '$name' already exists")
    }

    return name
  }

  def updateAutoScalingGroupDesiredCount(id: String, diff: Long): String = {
    val name = getAutoScalingGroupName(id)
    try {
      val result = client.describeAutoScalingGroups(
        new DescribeAutoScalingGroupsRequest()
          .withAutoScalingGroupNames(name)
      )

      val newDesiredCount = result.getAutoScalingGroups.asScala.headOption match {
        case None => sys.error(s"Problem finding autoscaling group $name")
        case Some(asg) => asg.getDesiredCapacity + diff.toInt
      }

      client.updateAutoScalingGroup(
        new UpdateAutoScalingGroupRequest()
          .withAutoScalingGroupName(name)
          .withDesiredCapacity(newDesiredCount)
      )
    } catch {
      case e: ScalingActivityInProgressException => Logger.error(s"Error scaling group $name to count $diff")
      case e: Throwable => {
        e.printStackTrace
        Logger.error(s"Error processing incoming queue message: ${e.getMessage}, Class: ${e.getClass.getName}")
      }
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
          .withVPCZoneIdentifier(settings.asgSubnets.mkString(","))
          .withNewInstancesProtectedFromScaleIn(false)
          .withHealthCheckGracePeriod(settings.asgHealthCheckGracePeriod)
          .withMinSize(settings.asgMinSize)
          .withMaxSize(settings.asgMaxSize)
          .withDesiredCapacity(settings.asgDesiredSize)
      )
    } catch {
      case e: AlreadyExistsException => Logger.error(s"Launch Configuration '$name' already exists")
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
