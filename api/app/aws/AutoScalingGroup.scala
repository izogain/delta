package io.flow.delta.aws

import io.flow.play.util.Config

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.autoscaling.model._
import play.api.Logger
import sun.misc.BASE64Encoder

import collection.JavaConverters._

@javax.inject.Singleton
class AutoScalingGroup @javax.inject.Inject() (
  config: Config,
  credentials: Credentials,
  configuration: Configuration
) {

  private[this] lazy val dockerHubToken = config.requiredString("dockerhub.delta.auth.token")
  private[this] lazy val dockerHubEmail = config.requiredString("dockerhub.delta.auth.email")

  lazy val client = new AmazonAutoScalingClient(credentials.aws, configuration.aws)
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

  def getLaunchConfigurationName(settings: Settings, id: String) = s"$id-ecs-lc-${settings.launchConfigImageId}"

  def getAutoScalingGroupName(id: String) = s"$id-ecs-auto-scaling-group"

  def createLaunchConfiguration(settings: Settings, id: String): String = {
    val name = getLaunchConfigurationName(settings, id)
    try {
      Logger.info(s"AWS AutoScalingGroup createLaunchConfiguration id[$id]")
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
      case e: AlreadyExistsException => println(s"Launch Configuration '$name' already exists")
    }

    return name
  }

  def deleteAutoScalingGroup(id: String): String = {
    val name = getAutoScalingGroupName(id)
    Logger.info(s"AWS delete ASG projectId[$id]")

    try {
      client.deleteAutoScalingGroup(
        new DeleteAutoScalingGroupRequest()
          .withAutoScalingGroupName(name)
          .withForceDelete(true)
      )
    } catch {
      case e: Throwable => Logger.error(s"Error deleting autoscaling group $name - Error: ${e.getMessage}")
    }

    name
  }

  def deleteLaunchConfiguration(settings: Settings, id: String): String = {
    val name = getLaunchConfigurationName(settings, id)
    Logger.info(s"AWS delete launch config projectId[$id]")

    try {
      client.deleteLaunchConfiguration(new DeleteLaunchConfigurationRequest().withLaunchConfigurationName(name))
    } catch {
      case e: Throwable => Logger.error(s"Error deleting launch configuration $name - Error: ${e.getMessage}")
    }

    name
  }

  def createAutoScalingGroup(settings: Settings, id: String, launchConfigName: String, loadBalancerName: String): String = {
    val name = getAutoScalingGroupName(id)
    try {
      Logger.info(s"AWS AutoScalingGroup createAutoScalingGroup id[$id]")
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
      case e: AlreadyExistsException => println(s"Launch Configuration '$name' already exists")
    }

    return name
  }

  def lcUserData(id: String): String = {
    val ecsClusterName = EC2ContainerService.getClusterName(id)

    return Seq(
      """#!/bin/bash""",
      """echo 'OPTIONS="-e env=production"' > /etc/sysconfig/docker""",
      s"""echo 'ECS_CLUSTER=${ecsClusterName}' >> /etc/ecs/ecs.config""",
      """echo 'ECS_ENGINE_AUTH_TYPE=dockercfg' >> /etc/ecs/ecs.config""",
      s"""echo 'ECS_ENGINE_AUTH_DATA={"https://index.docker.io/v1/":{"auth":"${dockerHubToken}","email":"${dockerHubEmail}"}}' >> /etc/ecs/ecs.config"""
    ).mkString("\n")
  }

}
