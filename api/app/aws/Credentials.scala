package io.flow.delta.aws

import com.amazonaws.auth.BasicAWSCredentials
import io.flow.play.util.DefaultConfig

trait Credentials {

  val awsCredentials = new BasicAWSCredentials(
    DefaultConfig.requiredString("aws.delta.access.key"),
    DefaultConfig.requiredString("aws.delta.secret.key")
  )

}
