package io.flow.delta.aws

import io.flow.delta.api.lib.RegistryClient
import io.flow.delta.v0.models.Version

import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.services.ecs.model._

import collection.JavaConverters._

import play.api.libs.concurrent.Akka
import play.api.Play.current

import scala.concurrent.Future

case class EC2ContainerService(registryClient: RegistryClient) extends Settings with Credentials {

  private[this] implicit val executionContext = Akka.system.dispatchers.lookup("ec2-context")

  private[this] lazy val client = new AmazonECSClient(awsCredentials)

  /**
  * Name creation helper functions
  **/
  def getClusterName(projectId: String): String = {
    return s"$projectId-cluster"
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
  def createCluster(projectId: String): String = {
    val name = getClusterName(projectId)
    client.createCluster(new CreateClusterRequest().withClusterName(name))
    return name
  }

  // scale to the desired count - can be up or down
  def scale(imageName: String, imageVersion: String, projectId: String, desiredCount: Long): Future[Unit] = {
    val cluster = getClusterName(projectId)

    for {
      taskDef <- registerTaskDefinition(imageName, imageVersion, projectId)
      service <- createService(imageName, imageVersion, projectId, taskDef)
      count <- updateServiceDesiredCount(cluster, service, desiredCount)
    } yield {
      // Nothing
    }
  }

  def updateServiceDesiredCount(cluster: String, service: String, desiredCount: Long): Future[Long] = {
    Future {
      client.updateService(
        new UpdateServiceRequest()
        .withCluster(cluster)
        .withService(service)
        .withDesiredCount(desiredCount.toInt)
      )
      desiredCount
    }
  }

  def getClusterInfo(projectId: String): Future[Seq[Version]] = {
    Future {
      val cluster = getClusterName(projectId)

      // TODO: How to make more functional?
      var serviceArns = scala.collection.mutable.ListBuffer.empty[List[String]]
      var hasMore: Boolean = true
      var nextToken: String = null // null nextToken gets the first page

      while (hasMore) {
        var result = client.listServices(
          new ListServicesRequest()
          .withCluster(cluster)
          .withNextToken(nextToken)
        )
        serviceArns += result.getServiceArns.asScala.toList

        Option(result.getNextToken) match {
          case Some(token) => {
            nextToken = token
          }
          case None => {
            hasMore = false
          }
        }
      }

      serviceArns.flatten.distinct.map{ serviceArn =>
        val service = client.describeServices(
          new DescribeServicesRequest()
          .withCluster(cluster)
          .withServices(Seq(serviceArn).asJava)
        ).getServices().asScala.headOption.getOrElse {
          sys.error(s"Service ARN $serviceArn does not exist for cluster $cluster")
        }

        client.describeTaskDefinition(
          new DescribeTaskDefinitionRequest()
          .withTaskDefinition(service.getTaskDefinition)
        ).getTaskDefinition().getContainerDefinitions().asScala.headOption match {
          case None => sys.error(s"No container definitions for task definition ${service.getTaskDefinition}")
          case Some(containerDef) => {
            val image = Util.parseImage(containerDef.getImage()).getOrElse {
              sys.error(s"Invalid image name[${containerDef.getImage()}] - could not parse version")
            }
            Version(image.version, service.getRunningCount.toInt)
          }
        }
      }
    }
  }

  def getServiceInfo(imageName: String, imageVersion: String, projectId: String): Option[Service] = {
    val cluster = getClusterName(projectId)
    val service = getServiceName(imageName, imageVersion)

    // should only be one thing, since we are passing cluster and service
    client.describeServices(
      new DescribeServicesRequest()
      .withCluster(cluster)
      .withServices(Seq(service).asJava)
    ).getServices().asScala.headOption
  }

  def registerTaskDefinition(imageName: String, imageVersion: String, projectId: String): Future[String] = {
    val taskName = getTaskName(imageName, imageVersion)
    val containerName = getContainerName(imageName, imageVersion)
    registryClient.getById(projectId).map {
      case None => {
        sys.error(s"project[$projectId] was not found in the registry")
      }

      case Some(application) => {
        val registryPorts = application.ports.headOption.getOrElse {
          sys.error(s"project[$projectId] does not have any ports in the registry")
        }

        client.registerTaskDefinition(
          new RegisterTaskDefinitionRequest()
          .withFamily(taskName)
          .withContainerDefinitions(
            Seq(
              new ContainerDefinition()
              .withName(containerName)
              .withImage(imageName + ":" + imageVersion)
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

        taskName
      }
    }
  }

  def getServiceInstances(imageName: String, imageVersion: String, projectId: String): Future[Seq[String]] = {
    val clusterName = getClusterName(projectId)
    val serviceName = getServiceName(imageName, imageVersion)

    Future {
      val containerInstances = client.describeTasks(
        new DescribeTasksRequest()
        .withCluster(clusterName)
        .withTasks(client.listTasks(
          new ListTasksRequest()
          .withCluster(clusterName)
          .withServiceName(serviceName)
        ).getTaskArns)
      ).getTasks.asScala.map(_.getContainerInstanceArn).asJava

      client.describeContainerInstances(
        new DescribeContainerInstancesRequest()
        .withCluster(clusterName)
        .withContainerInstances(containerInstances)
      ).getContainerInstances().asScala.map{containerInstance => containerInstance.getEc2InstanceId }
    }
  }

  def createService(imageName: String, imageVersion: String, projectId: String, taskDefinition: String): Future[String] = {
    val clusterName = getClusterName(projectId)
    val serviceName = getServiceName(imageName, imageVersion)
    val containerName = getContainerName(imageName, imageVersion)
    val loadBalancerName = ElasticLoadBalancer(registryClient).getLoadBalancerName(projectId)

    registryClient.getById(projectId).map {
      case None => {
        sys.error(s"project[$projectId] was not found in the registry")
      }

      case Some(application) => {
        val registryPorts = application.ports.headOption.getOrElse {
          sys.error(s"project[$projectId] does not have any ports in the registry")
        }

        val resp = client.describeServices(
          new DescribeServicesRequest()
            .withCluster(clusterName)
            .withServices(Seq(serviceName).asJava)
        )

        // if service doesn't exist in the cluster
        if (!resp.getFailures().isEmpty()) {
          client.createService(
            new CreateServiceRequest()
            .withServiceName(serviceName)
            .withCluster(clusterName)
            .withDesiredCount(createServiceDesiredCount)
            .withRole(serviceRole)
            .withTaskDefinition(taskDefinition)
            .withDeploymentConfiguration(
              new DeploymentConfiguration()
              .withMinimumHealthyPercent(99)
              .withMaximumPercent(100)
            )
            .withLoadBalancers(
              Seq(
                new LoadBalancer()
                .withContainerName(containerName)
                .withLoadBalancerName(loadBalancerName)
                .withContainerPort(registryPorts.internal.toInt)
              ).asJava
            )
          )
        }

        serviceName
      }
    }
  }

  def summary(service: Service): String = {
    val status = service.getStatus
    val running = service.getRunningCount
    val desired = service.getDesiredCount
    val pending = service.getPendingCount

    s"status[$status] running[$running] desired[$desired] pending[$pending]"
  }


}
