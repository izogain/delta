FROM flowdocker/play:0.0.40

ADD . /opt/play

WORKDIR /opt/play

RUN sbt 'project api' clean stage

WORKDIR api/target/universal/stage

ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "--service", "play", "delta", "bin/delta-api"]
