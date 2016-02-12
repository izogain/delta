package io.flow.delta.api.lib

case class Repo(
  owner: String,   // e.g. flowcommerce
  project: String  // e.g. registry
) {

  val awsName = owner + "-" + project

  override def toString() = s"$owner/$project"

}
