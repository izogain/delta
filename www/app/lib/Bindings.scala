package io.flow.delta.www.lib


import play.api.{Environment, Configuration, Mode}
import play.api.inject.Module

class DeltaClientProviderModule extends Module {

  def bindings(env: Environment, conf: Configuration) = {
    env.mode match {
      case Mode.Prod | Mode.Dev => Seq(
        bind[DeltaClientProvider].to[DefaultDeltaClientProvider]
      )
      case Mode.Test => Seq(
        // TODO: Add mock
        bind[DeltaClientProvider].to[DeltaClientProvider]
      )
    }
  }

}
