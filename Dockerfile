FROM flowcommerce/play:0.0.8

ADD . /opt/play

WORKDIR /opt/play

RUN sbt clean stage
  
ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "run", "play", "delta", "api/target/universal/stage/bin/delta-api"]
