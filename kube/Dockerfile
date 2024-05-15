
FROM envoyproxy/envoy:v1.29-latest AS builder
FROM ubuntu:latest

# RUN wget -q -O /etc/apk/keys/sgerrand.rsa.pub https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub && \
#     wget https://github.com/sgerrand/alpine-pkg-glibc/releases/download/2.29-r0/glibc-2.29-r0.apk && \
#     apk add glibc-2.29-r0.apk &&


COPY --from=builder /usr/local/bin/envoy /usr/bin/

USER root:root

RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    apt-get clean;

RUN adduser dodex --disabled-password

RUN mkdir /envoy && mkdir /data && chown 1000 /data && mkdir /data/db
COPY envoy.yaml /etc/envoy/envoy.yaml
RUN chmod go+r /etc/envoy/envoy.yaml && chmod o+w /data/db

EXPOSE 8088
EXPOSE 8071
EXPOSE 9901

COPY run_dodex.sh /usr/bin
RUN chmod o+x /usr/bin/run_dodex.sh

USER dodex:dodex

RUN mkdir /home/dodex/quarkus && mkdir /home/dodex/quarkus/logs

VOLUME ~

COPY dodex-quarkus-3.2.0-runner.jar /home/dodex/quarkus/dodex-quarkus-3.2.0-runner.jar

WORKDIR /home/dodex/quarkus

ENV JAVA_HOME /usr/lib/jvm/java-21-openjdk-amd64/
# make quarkus http run on this address
ENV DOCKER_HOST 0.0.0.0
# Can change to either "postgres" or "mariadb" or override in docker create/run
ENV DEFAULT_DB=h2
ENV USE_HANDICAP=true

USER dodex

CMD /usr/bin/bash -c /usr/bin/run_dodex.sh


