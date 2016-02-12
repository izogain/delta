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

  def getClusterName(projectName: String): String = s"$projectName-ecs-cluster"

  def getContainerName(projectName: String): String = s"$projectName-ecs-container"

  def getTaskName(projectName: String): String = s"$projectName-ecs-task"

  def createCluster(projectName: String): String = {
    val name = getClusterName(projectName)
    client.createCluster(new CreateClusterRequest().withClusterName(name))
    return name
  }

  def getServiceName(id: String, projectName: String): String = {
    val pattern = "(\\w+)/(\\w+):(.+)".r
    val pattern(o, p, tag) = id
    return s"$projectName-api-ecs-service-${tag.replaceAll("[.]","-")}"
  }

  def scaleUp(id: String, projectName: String) {
    val service = getServiceName(id, projectName)
    val cluster = getClusterName(projectName)

    client.updateService(
      new UpdateServiceRequest()
        .withCluster(cluster)
        .withService(service)
        .withDesiredCount(maxScaleUpDesiredCount)
    )
  }

  def getServiceInfo(id: String, projectName: String): Service = {
    val service = getServiceName(id, projectName)
    val cluster = getClusterName(projectName)

    // should only be one thing, since we are passing cluster and service
    client.describeServices(
      new DescribeServicesRequest()
        .withCluster(cluster)
        .withServices(Seq(service).asJava)
    ).getServices().asScala.head
  }

  def registerTaskDefinition(id: String, projectName: String): String = {
    val taskName = getTaskName(projectName)
    val containerName = getContainerName(projectName)
    val registryPorts = RegistryClient.ports(projectName)

    client.registerTaskDefinition(
      new RegisterTaskDefinitionRequest()
        .withFamily(taskName)
        .withContainerDefinitions(
          Seq(
            new ContainerDefinition()
              .withName(containerName)
              .withImage(id)
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

  def createService(id: String, projectName: String, taskDefinition: String): String = {
    val serviceName = getServiceName(id, projectName)
    val clusterName = getClusterName(projectName)
    val containerName = getContainerName(projectName)
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
