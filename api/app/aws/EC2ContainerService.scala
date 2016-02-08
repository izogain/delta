package aws

import util.RegistryClient

import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.services.ecs.model._

import collection.JavaConverters._

object EC2ContainerService {
  lazy val client = new AmazonECSClient()

  // stuff to make configurable
  lazy val containerMemory = 500
  lazy val serviceRole = "ecsServiceRole"
  lazy val serviceDesiredCount = 1

  def getClusterName(projectId: String): String = s"$projectId-api-ecs-cluster"

  def createCluster(projectId: String): String = {
    val name = getClusterName(projectId)
    client.createCluster(new CreateClusterRequest().withClusterName(name))
    return name
  }

  def getServiceName(id: String, projectId: String, tag: String): String = {
    return s"$projectId-api-ecs-service-${tag.replaceAll("[.]","-")}"
  }

  def getServiceInfo(id: String): Service = {
    val (organization, projectId, tag) = getComponentsFromImageId(id)
    // should only be one thing, since we are passing cluster and service
    val service = getServiceName(id, projectId, tag)
    client.describeServices(
      new DescribeServicesRequest()
        .withCluster(getClusterName(projectId))
        .withServices(Seq(service).asJava)
    ).getServices().asScala.head
  }

  def registerTaskDefinition(id: String): String = {
    val (organization, projectId, tag) = getComponentsFromImageId(id)
    val taskName = s"$projectId-api-ecs-task"
    val containerName = s"$projectId-api-ecs-container"
    val registryPorts = RegistryClient.ports(projectId)

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

  def createService(id: String, taskDefinition: String): String = {
    val (organization, projectId, tag) = getComponentsFromImageId(id)
    val serviceName = getServiceName(id, projectId, tag)
    val clusterName = s"$projectId-api-ecs-cluster"
    val containerName = s"$projectId-api-ecs-container"
    val loadBalancerName = s"$projectId-api-ecs-lb"

    return client.createService(
      new CreateServiceRequest()
        .withServiceName(serviceName)
        .withCluster(clusterName)
        .withDesiredCount(serviceDesiredCount)
        .withRole(serviceRole)
        .withTaskDefinition(taskDefinition)
        .withLoadBalancers(
          Seq(
            new LoadBalancer()
              .withContainerName(containerName)
              .withLoadBalancerName(loadBalancerName)
              .withContainerPort(RegistryClient.ports(projectId).internal.toInt)
          ).asJava
        )
    ).getService().getServiceName()
  }

  def getComponentsFromImageId(id: String): (String,String,String) = {
    val pattern = "(\\w+)/(\\w+):(.+)".r
    val pattern(organization, projectId, tag) = id
    return (organization, projectId, tag)
  }

}
