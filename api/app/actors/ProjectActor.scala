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
      println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Launch Configuration:  [${lc}]")

      val elb = createLoadBalancer(id)
      println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Load Balancer: [${elb}]")

      val asg = createAutoScalingGroup(id, lc, elb)
      println(s"[ProjectActor.Messages.ConfigureEC2] Done - Project: [$id], EC2 Auto-Scaling Group: [${asg}]")
    }

    // Create ECS cluster for a project (id: user, fulfillment, splashpage, etc)
    case msg @ ProjectActor.Messages.ConfigureECS(id: String) => withVerboseErrorHandler(msg.toString) {
      val cluster = createCluster(id)
      println(s"[ProjectActor.Messages.ConfigureECS] Done - Project: [$id], ECS Cluster: [${cluster}]")
    }
  }

  def createLaunchConfiguration(id: String): String = {
    return AutoScalingGroup.createLaunchConfiguration(id)
  }

  def createLoadBalancer(id: String): String = {
    return ElasticLoadBalancer.createLoadBalancerAndHealthCheck(id)
  }

  def createAutoScalingGroup(id: String, launchConfigName: String, loadBalancerName: String): String = {
    return AutoScalingGroup.createAutoScalingGroup(id, launchConfigName, loadBalancerName)
  }

  def createCluster(id: String): String = {
    return EC2ContainerService.createCluster(id)
  }

}
