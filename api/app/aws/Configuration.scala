package io.flow.delta.aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.retry.RetryPolicy

@javax.inject.Singleton
class Configuration @javax.inject.Inject() () {

  val aws = new ClientConfiguration().withRetryPolicy(
    new RetryPolicy(
      null, // Retry condition on whether a specific request and exception should be retried. If null value is specified, the SDK' default retry condition is used.
      null, // Back-off strategy for controlling how long the next retry should wait. If null value is specified, the SDK' default exponential back-off strategy is used.
      6,    // Maximum number of retry attempts for failed requests
      false // Whether this retry policy should honor the max error retry set by ClientConfiguration.setMaxErrorRetry(int)
    )
  )
}
