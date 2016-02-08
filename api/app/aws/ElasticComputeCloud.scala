package aws

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model._

import collection.JavaConverters._

object ElasticComputeCloud {
  lazy val client = new AmazonEC2Client()

  // Add stuff here....
}
