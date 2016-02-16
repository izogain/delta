package io.flow.delta.aws

import io.flow.delta.v0.models.Version

import util.RegistryClient

import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.services.ecs.model._

import collection.JavaConverters._

object EC2ContainerService extends Settings {
  lazy val client = new AmazonECSClient()

  /**
  * Name creation helper functions
  **/
  def getClusterName(projectName: String): String = {
    return s"$projectName-cluster"
  }

  def getBaseName(imageName: String, imageVersion: String): String = {
    return Seq(
      s"${imageName.replaceAll("[/]","-")}", // flow/registry becomes flow-registry
      s"${imageVersion.replaceAll("[.]","-")}" // 1.2.3 becomes 1-2-3
    ).mkString("-")
  }

  def getContainerName(imageName: String, imageVersion: String): String = {
    return s"${getBaseName(imageName, imageVersion)}-container"
  }

  def getTaskName(imageName: String, imageVersion: String): String = {
    return s"${getBaseName(imageName, imageVersion)}-task"
  }

  def getServiceName(imageName: String, imageVersion: String): String = {
    return s"${getBaseName(imageName, imageVersion)}-service"
  }

  /**
  * Functions that interact with AWS ECS
  **/
  def createCluster(projectName: String): String = {
    val name = getClusterName(projectName)
    client.createCluster(new CreateClusterRequest().withClusterName(name))
    return name
  }

  def scaleUp(imageName: String, imageVersion: String, projectName: String, count: Int = maxScaleUpDesiredCount) {
    val cluster = getClusterName(projectName)
    scaleDownRunningServices(cluster)

    // Scale up the new service
    val newService = getServiceName(imageName, imageVersion)
    client.updateService(
      new UpdateServiceRequest()
      .withCluster(cluster)
      .withService(newService)
      .withDesiredCount(count)
    )
  }

  def scaleDownRunningServices(cluster: String) {
    // As part of scaling up a new service, we need to scale down the old service
    // Until traffic management gets better, set desired count of old service to 0
    client.describeServices(
      new DescribeServicesRequest()
      .withCluster(cluster)
    ).getServices().asScala.foreach{service =>
      if (service.getRunningCount() > 0) {
        client.updateService(
          new UpdateServiceRequest()
          .withCluster(cluster)
          .withDesiredCount(0)
        )
      }
    }
  }

  def getClusterInfo(projectId: String): Seq[Version] = {
    val cluster = getClusterName(projectId)

    client.listServices(
      new ListServicesRequest()
      .withCluster(cluster)
    ).getServiceArns().asScala.map{serviceArn =>
      val service = client.describeServices(
        new DescribeServicesRequest()
        .withCluster(cluster)
        .withServices(Seq(serviceArn).asJava)
      ).getServices().asScala.head

      val image = client.describeTaskDefinition(
        new DescribeTaskDefinitionRequest()
        .withTaskDefinition(service.getTaskDefinition)
      ).getTaskDefinition().getContainerDefinitions().asScala.head.getImage()

      // image name = "flow/user:0.0.1"
      // o = flow, p = user, version = 0.0.1
      val pattern = "(\\w+)/(\\w+):(.+)".r
      val pattern(o, p, version) = image
      Version(version, service.getRunningCount.toInt)
    }
  }

  def getServiceInfo(imageName: String, imageVersion: String, projectName: String): Service = {
    val cluster = getClusterName(projectName)
    val service = getServiceName(imageName, imageVersion)

    // should only be one thing, since we are passing cluster and service
    client.describeServices(
      new DescribeServicesRequest()
      .withCluster(cluster)
      .withServices(Seq(service).asJava)
    ).getServices().asScala.head
  }

  def registerTaskDefinition(imageName: String, imageVersion: String, projectName: String): String = {
    val taskName = getTaskName(imageName, imageVersion)
    val containerName = getContainerName(imageName, imageVersion)
    val registryPorts = RegistryClient.ports(projectName)

    client.registerTaskDefinition(
      new RegisterTaskDefinitionRequest()
      .withFamily(taskName)
      .withContainerDefinitions(
        Seq(
          new ContainerDefinition()
          .withName(containerName)
          .withImage(imageName)
          .withMemory(containerMemory)
          .withPortMappings(
            Seq(
              new PortMapping()
              .withContainerPort(registryPorts.internal.toInt)
              .withHostPort(registryPorts.external.toInt)
            ).asJava
          )
          .withCommand(Seq("production").asJava)
        ).asJava
      )
    )

    return taskName
  }

  def createService(imageName: String, imageVersion: String, projectName: String, taskDefinition: String): String = {
    val clusterName = getClusterName(projectName)
    val serviceName = getServiceName(imageName, imageVersion)
    val containerName = getContainerName(imageName, imageVersion)
    val loadBalancerName = ElasticLoadBalancer.getLoadBalancerName(projectName)

    return client.createService(
      new CreateServiceRequest()
      .withServiceName(serviceName)
      .withCluster(clusterName)
      .withDesiredCount(createServiceDesiredCount)
      .withRole(serviceRole)
      .withTaskDefinition(taskDefinition)
      .withLoadBalancers(
        Seq(
          new LoadBalancer()
          .withContainerName(containerName)
          .withLoadBalancerName(loadBalancerName)
          .withContainerPort(RegistryClient.ports(projectName).internal.toInt)
        ).asJava
      )
    ).getService().getServiceName()
  }

}
