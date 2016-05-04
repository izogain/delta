package io.flow.delta.aws

import io.flow.delta.api.lib.RegistryClient
import io.flow.delta.v0.models.Version

import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.services.ecs.model._

import collection.JavaConverters._

import play.api.libs.concurrent.Akka
import play.api.Logger
import play.api.Play.current

import org.joda.time.DateTime

import scala.concurrent.Future

object EC2ContainerService {

  /**
    * Name creation helper functions
    **/
  def getClusterName(projectId: String): String = {
    return s"$projectId-cluster"
  }

}


@javax.inject.Singleton
case class EC2ContainerService @javax.inject.Inject() (
  credentials: Credentials,
  registryClient: RegistryClient
) {

  private[this] implicit val executionContext = Akka.system.dispatchers.lookup("ec2-context")

  private[this] lazy val client = new AmazonECSClient(credentials.aws)

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
  def removeOldServices(projectId: String, nextToken: Option[String] = None): Future[Unit] = {
    Future {
      try {
        val cluster = EC2ContainerService.getClusterName(projectId)

        val baseRequest = new ListServicesRequest().withCluster(cluster)
        val request = nextToken match {
          case None => baseRequest
          case Some(token) => baseRequest.withNextToken(token)
        }

        Logger.info(s"AWS EC2ContainerService listServices projectId[$projectId]")
        val result = client.listServices(request)
        result.getServiceArns.asScala.map{ serviceName =>
          getServiceInfo(cluster, serviceName).map{ service =>
            service.getEvents.asScala.headOption match {
              case None => // do nothing
              case Some(event) => {
                // if there are no instances running, desired count is zero,
                // and has been 2 weeks since something happened with the service
                // let's just delete the service
                val eventDateTime = new DateTime(event.getCreatedAt)
                val twoWeeksAgo = new DateTime().minusWeeks(2)

                if (service.getDesiredCount == 0 && service.getRunningCount == 0 && eventDateTime.compareTo(twoWeeksAgo) < 0) {
                  Logger.info(s"AWS EC2ContainerService deleteService projectId[$projectId]")
                  client.deleteService(new DeleteServiceRequest().withCluster(cluster).withService(service.getServiceName))
                }
              }
            }
          }
        }

        if (result.getNextToken != null) {
          removeOldServices(projectId, Some(result.getNextToken))
        }
      } catch {
        case e: Throwable => sys.error(s"Removing old services for $projectId: $e")
      }
    }
  }

  def updateContainerAgent(projectId: String): Future[Seq[String]] = {
    Future {
      try {
        val cluster = EC2ContainerService.getClusterName(projectId)

        // find all the container instances for this cluster
        Logger.info(s"AWS EC2ContainerService listContainerInstances projectId[$projectId]")
        val containerInstanceArns = client.listContainerInstances(
          new ListContainerInstancesRequest()
          .withCluster(cluster)
        ).getContainerInstanceArns.asScala

        // call update for each container instance
        containerInstanceArns.map{ containerInstanceArn =>
          Logger.info(s"AWS EC2ContainerService updateContainerAgent projectId[$projectId]")
          val result = client.updateContainerAgent(
            new UpdateContainerAgentRequest()
            .withCluster(cluster)
            .withContainerInstance(containerInstanceArn)
          )

          containerInstanceArn
        }
      } catch {
        case e: UpdateInProgressException => Nil
        case e: NoUpdateAvailableException => Nil
        case e: Throwable => sys.error(s"Error upgrading container agent for $projectId: $e")
      }
    }
  }

  def createCluster(projectId: String): String = {
    val name = EC2ContainerService.getClusterName(projectId)
    Logger.info(s"AWS EC2ContainerService createCluster projectId[$projectId]")
    client.createCluster(new CreateClusterRequest().withClusterName(name))
    return name
  }

  // scale to the desired count - can be up or down
  def scale(
    settings: Settings,
    imageName: String,
    imageVersion: String,
    projectId: String,
    desiredCount: Long
  ): Future[Unit] = {
    val cluster = EC2ContainerService.getClusterName(projectId)

    for {
      taskDef <- registerTaskDefinition(settings, imageName, imageVersion, projectId)
      service <- createService(settings, imageName, imageVersion, projectId, taskDef)
      count <- updateServiceDesiredCount(cluster, service, desiredCount)
    } yield {
      // Nothing
    }
  }

  def updateServiceDesiredCount(cluster: String, service: String, desiredCount: Long): Future[Long] = {
    Future {
      Logger.info(s"AWS EC2ContainerService updateService cluster[$cluster]")
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
      val cluster = EC2ContainerService.getClusterName(projectId)

      // TODO: How to make more functional?
      var serviceArns = scala.collection.mutable.ListBuffer.empty[List[String]]
      var hasMore: Boolean = true
      var nextToken: String = null // null nextToken gets the first page

      while (hasMore) {
        Logger.info(s"AWS EC2ContainerService listServices projectId[$projectId]")
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

      serviceArns.flatten.distinct.map { serviceArn =>
        Logger.info(s"AWS EC2ContainerService describeServices projectId[$projectId] serviceArn[$serviceArn]")
        val service = client.describeServices(
          new DescribeServicesRequest()
          .withCluster(cluster)
          .withServices(Seq(serviceArn).asJava)
        ).getServices().asScala.headOption.getOrElse {
          sys.error(s"Service ARN $serviceArn does not exist for cluster $cluster")
        }

        Logger.info(s"AWS EC2ContainerService describeTaskDefinition projectId[$projectId] serviceArn[$serviceArn]")
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
    val cluster = EC2ContainerService.getClusterName(projectId)
    val service = getServiceName(imageName, imageVersion)
    getServiceInfo(cluster, service)
  }

  def getServiceInfo(cluster: String, service: String): Option[Service] = {
    // should only be one thing, since we are passing cluster and service
    Logger.info(s"AWS EC2ContainerService describeServices cluster[$cluster]")
    client.describeServices(
      new DescribeServicesRequest()
        .withCluster(cluster)
        .withServices(Seq(service).asJava)
    ).getServices().asScala.headOption
  }

  def registerTaskDefinition(
    settings: Settings,
    imageName: String,
    imageVersion: String,
    projectId: String
  ): Future[String] = {
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

        Logger.info(s"AWS EC2ContainerService registerTaskDefinition projectId[$projectId] imageName[$imageName] imageVersion[$imageVersion]")
        client.registerTaskDefinition(
          new RegisterTaskDefinitionRequest()
          .withFamily(taskName)
          .withContainerDefinitions(
            Seq(
              new ContainerDefinition()
              .withName(containerName)
              .withImage(imageName + ":" + imageVersion)
              .withMemory(settings.containerMemory)
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
    val clusterName = EC2ContainerService.getClusterName(projectId)
    val serviceName = getServiceName(imageName, imageVersion)

    Future {
      Logger.info(s"AWS EC2ContainerService describeTasks projectId[$projectId] imageName[$imageName] imageVersion[$imageVersion]")
      val containerInstances = client.describeTasks(
        new DescribeTasksRequest()
        .withCluster(clusterName)
        .withTasks(client.listTasks(
          new ListTasksRequest()
          .withCluster(clusterName)
          .withServiceName(serviceName)
        ).getTaskArns)
      ).getTasks.asScala.map(_.getContainerInstanceArn).asJava

      Logger.info(s"AWS EC2ContainerService describeContainerInstances projectId[$projectId] imageName[$imageName] imageVersion[$imageVersion]")
      client.describeContainerInstances(
        new DescribeContainerInstancesRequest()
        .withCluster(clusterName)
        .withContainerInstances(containerInstances)
      ).getContainerInstances().asScala.map{containerInstance => containerInstance.getEc2InstanceId }
    }
  }

  def createService(
    settings: Settings,
    imageName: String,
    imageVersion: String,
    projectId: String,
    taskDefinition: String
  ): Future[String] = {
    val clusterName = EC2ContainerService.getClusterName(projectId)
    val serviceName = getServiceName(imageName, imageVersion)
    val containerName = getContainerName(imageName, imageVersion)
    val loadBalancerName = ElasticLoadBalancer.getLoadBalancerName(projectId)

    registryClient.getById(projectId).map {
      case None => {
        sys.error(s"project[$projectId] was not found in the registry")
      }

      case Some(application) => {
        val registryPorts = application.ports.headOption.getOrElse {
          sys.error(s"project[$projectId] does not have any ports in the registry")
        }

        Logger.info(s"AWS EC2ContainerService describeServices projectId[$projectId] imageName[$imageName] imageVersion[$imageVersion]")
        val resp = client.describeServices(
          new DescribeServicesRequest()
            .withCluster(clusterName)
            .withServices(Seq(serviceName).asJava)
        )

        // if service doesn't exist in the cluster
        if (!resp.getFailures().isEmpty()) {
          Logger.info(s"AWS EC2ContainerService createService projectId[$projectId] imageName[$imageName] imageVersion[$imageVersion]")
          client.createService(
            new CreateServiceRequest()
            .withServiceName(serviceName)
            .withCluster(clusterName)
            .withDesiredCount(settings.createServiceDesiredCount)
            .withRole(settings.serviceRole)
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
