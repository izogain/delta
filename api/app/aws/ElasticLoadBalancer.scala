package aws

import util.RegistryClient

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
import com.amazonaws.services.elasticloadbalancing.model._

import collection.JavaConverters._

object ElasticLoadBalancer {
  lazy val client = new AmazonElasticLoadBalancingClient()

  // vals to be used
  val elbSubnets = Seq("subnet-4a8ee313", "subnet-7d2f1547", "subnet-c99a0de2", "subnet-dc2961ab")
  val elbSecurityGroups = Seq("sg-4aead833")

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
