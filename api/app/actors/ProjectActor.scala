package actors

import aws._
import play.api.libs.concurrent.Akka
import akka.actor.{Actor,Props}
import play.api.Logger
import play.api.Play.current
import scala.concurrent.ExecutionContext

object ProjectActor {

  def props() = Props(new ProjectActor())

  lazy val ref = Akka.system.actorOf(props(), "project")

  object Messages {
    case class Data(id: String)
    case class ConfigureEC2(id: String) // One-time EC2 setup
    case class ConfigureECS(id: String) // One-time ECS setup
  }

}

class ProjectActor extends Actor with Util {

  implicit val projectActorExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("project-actor-context")

  def receive = {
    // Configure EC2 LC, ELB, ASG for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureEC2(id: String) => withVerboseErrorHandler(msg.toString) {
      val lc = createLaunchConfiguration(id)
      val elb = createLoadBalancer(id)
      createAutoScalingGroup(id, lc, elb)
    }

    // Create ECS cluster for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureECS(id: String) => withVerboseErrorHandler(msg.toString) {
      createCluster(id)
    }
  }

  def createLaunchConfiguration(id: String): String = {
    val lc = AutoScalingGroup.createLaunchConfiguration(id)
    println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Launch Configuration: [${lc}]")
    return lc
  }

  def createLoadBalancer(id: String): String = {
    val elb = ElasticLoadBalancer.createLoadBalancerAndHealthCheck(id)
    println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Load Balancer: [${elb}]")
    return elb
  }

  def createAutoScalingGroup(id: String, launchConfigName: String, loadBalancerName: String) {
    val asg = AutoScalingGroup.createAutoScalingGroup(id, launchConfigName, loadBalancerName)
    println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Auto-Scaling Group: [${asg}]")
  }

  def createCluster(id: String) {
    val cluster = EC2ContainerService.createCluster(id)
    println(s"[ProjectActor.Messages.ConfigureECS] Done - Project: [$id], ECS Cluster: [${cluster}]")
  }

}
