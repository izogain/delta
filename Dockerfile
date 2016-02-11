FROM giltarchitecture/ubuntu-jvm:0.6

MAINTAINER mbryzek@alum.mit.edu

ADD . /usr/share/dependency

WORKDIR /usr/share/dependency

RUN sbt -Dsbt.ivy.home=.ivy2 clean stage

RUN ln -s /usr/share/dependency/api/target/universal/stage /usr/share/dependency-api
RUN ln -s /usr/share/dependency/www/target/universal/stage /usr/share/dependency-www
