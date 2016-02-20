package io.flow.delta.aws

import com.amazonaws.auth.BasicAWSCredentials
import io.flow.play.util.DefaultConfig

trait Credentials {

  private[this] val config = play.api.Play.current.injector.instanceOf[DefaultConfig]

  val awsCredentials = new BasicAWSCredentials(
    config.requiredString("aws.delta.access.key"),
    config.requiredString("aws.delta.secret.key")
  )

}
