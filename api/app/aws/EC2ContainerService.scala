package io.flow.delta.aws

import util.RegistryClient

import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.services.ecs.model._

import collection.JavaConverters._

object EC2ContainerService {
  lazy val client = new AmazonECSClient()

  val containerMemory = 500
  val serviceRole = "ecsServiceRole"
  val createServiceDesiredCount = 1
  val maxScaleUpDesiredCount = 3

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

  def scaleUp(imageName: String, imageVersion: String, projectName: String) {
    val cluster = getClusterName(projectName)
    val service = getServiceName(imageName, imageVersion)

    client.updateService(
      new UpdateServiceRequest()
        .withCluster(cluster)
        .withService(service)
        .withDesiredCount(maxScaleUpDesiredCount)
    )
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
