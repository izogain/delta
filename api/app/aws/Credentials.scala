package io.flow.delta.aws

import com.amazonaws.auth.BasicAWSCredentials
import io.flow.play.util.Config

@javax.inject.Singleton
class Credentials @javax.inject.Inject() (
  config: Config
) {

  val aws = new BasicAWSCredentials(
    config.requiredString("aws.delta.access.key"),
    config.requiredString("aws.delta.secret.key")
  )

}
