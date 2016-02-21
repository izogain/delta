FROM flowdocker/play:0.0.14

ADD . /opt/play

WORKDIR /opt/play

RUN sbt clean www/stage

ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "run", "play", "delta", "www", "target/universal/stage/bin/delta-www"]
