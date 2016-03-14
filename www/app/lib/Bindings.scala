package io.flow.delta.www.lib

import io.flow.play.clients.DefaultTokenClient
import play.api.{Environment, Configuration, Mode}
import play.api.inject.Module

class DeltaClientProviderModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[DeltaClientProvider].to[DefaultDeltaClientProvider]//,
//        bind[DefaultTokenClient].to[DefaultDeltaClientProvider]
      )
      case Mode.Test => Seq(
        // TODO: Add mock
        bind[DeltaClientProvider].to[DeltaClientProvider]//,
//        bind[DefaultTokenClient].to[DefaultDeltaClientProvider]
      )
    }
  }

}
