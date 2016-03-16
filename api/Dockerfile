FROM flowdocker/play:0.0.37

RUN sbt 'project api' clean stage

ADD target/universal/stage /opt/play

WORKDIR /opt/play

ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "run", "play", "delta", "api", "bin/delta-api"]
