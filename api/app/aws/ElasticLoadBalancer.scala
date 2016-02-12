package io.flow.delta.aws

import util.RegistryClient

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model._

import collection.JavaConverters._

object ElasticLoadBalancer extends Settings {
  lazy val client = new AmazonElasticLoadBalancingClient()

  def getLoadBalancerName(id: String): String = s"$id-ecs-lb"

  def createLoadBalancerAndHealthCheck(id: String): String = {
    // create the load balancer first, then configure healthcheck
    // they do not allow this in a single API call
    val name = getLoadBalancerName(id)
    val externalPort = RegistryClient.ports(id).external
    createLoadBalancer(name, externalPort)
    configureHealthCheck(name, externalPort)
    return name
  }

  def createLoadBalancer(name: String, externalPort: Long) {
    val elbListeners = Seq(
      new Listener()
        .withProtocol("HTTP")
        .withInstanceProtocol("HTTP")
        .withLoadBalancerPort(80)
        .withInstancePort(externalPort.toInt)
    )

    try {
      val result = client.createLoadBalancer(
        new CreateLoadBalancerRequest()
          .withLoadBalancerName(name)
          .withListeners(elbListeners.asJava)
          .withSubnets(subnets.asJava)
          .withSecurityGroups(securityGroups.asJava)
      )
      println(s"Created Load Balancer: ${result.getDNSName}")
    } catch {
      case e: DuplicateLoadBalancerNameException => println(s"Launch Configuration '$name' already exists")
    }
  }

  def configureHealthCheck(name: String, externalPort: Long) {
    try {
      client.configureHealthCheck(
        new ConfigureHealthCheckRequest()
          .withLoadBalancerName(name)
          .withHealthCheck(
            new HealthCheck()
              .withHealthyThreshold(2)
              .withInterval(30)
              .withTarget(s"HTTP:$externalPort/_internal_/healthcheck")
              .withTimeout(5)
              .withUnhealthyThreshold(2)
          )
      )
    } catch {
      case e: LoadBalancerNotFoundException => sys.error("Cannoy find load balancer $name")
    }
  }

}
