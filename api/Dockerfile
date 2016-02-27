FROM flowdocker/play:0.0.21

ADD . /opt/play

WORKDIR /opt/play

RUN sbt 'project api' clean stage

ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "run", "play", "delta", "api", "target/universal/stage/bin/delta-api"]
