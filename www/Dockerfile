FROM flowdocker/play:0.0.43

RUN apt-get install -y curl
RUN curl -sL https://deb.nodesource.com/setup_5.x | bash -
RUN apt-get install -y nodejs
RUN update-alternatives --install /usr/bin/node node /usr/bin/nodejs 10

ADD . /opt/play

WORKDIR /opt/play

RUN sbt 'project www' clean stage

WORKDIR www/target/universal/stage

ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "--service", "play", "delta", "bin/delta-www"]
