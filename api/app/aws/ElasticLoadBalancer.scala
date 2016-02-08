package aws

import util.RegistryClient

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model._

import collection.JavaConverters._

object ElasticLoadBalancer {
  lazy val client = new AmazonElasticLoadBalancingClient()

  // TODO: Assume that these are already created?
  // Should we create something to check?
  lazy val elbSubnets = Seq("subnet-4a8ee313", "subnet-7d2f1547", "subnet-c99a0de2", "subnet-dc2961ab")
  lazy val elbSecurityGroups = Seq("sg-4aead833")

  def createLoadBalancerAndHealthCheck(id: String): String = {
    // create the load balancer first, then configure healthcheck
    // they do not allow this in a single API call
    val name = s"$id-api-ecs-lb"
    val externalPort = RegistryClient.ports(id).external
    createLoadBalancer(name, externalPort)
    configureHealthCheck(name, externalPort)
    return name
  }

  def createLoadBalancer(name: String, externalPort: Long) {
    val elbListeners = Seq(
      new Listener()
        .withProtocol("HTTP")
        .withLoadBalancerPort(80)
        .withInstanceProtocol("HTTP")
        .withInstancePort(externalPort.toInt)
    )

    client.createLoadBalancer(
      new CreateLoadBalancerRequest()
        .withLoadBalancerName(name)
        .withListeners(elbListeners.asJava)
        .withSubnets(elbSubnets.asJava)
        .withSecurityGroups(elbSecurityGroups.asJava)
    )
  }

  def configureHealthCheck(name: String, externalPort: Long) {
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
  }

}
