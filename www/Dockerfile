FROM flowdocker/play:0.0.21

ADD . /opt/play

WORKDIR /opt/play

RUN sbt 'project www' clean stage

ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "run", "play", "delta", "www", "target/universal/stage/bin/delta-www"]
