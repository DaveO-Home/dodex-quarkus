
FROM envoyproxy/envoy:v1.25.0 AS builder
FROM openjdk:17-alpine

RUN wget -q -O /etc/apk/keys/sgerrand.rsa.pub https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub && \
    wget https://github.com/sgerrand/alpine-pkg-glibc/releases/download/2.29-r0/glibc-2.29-r0.apk && \
    apk add glibc-2.29-r0.apk && mkdir /envoy && mkdir /data && mkdir /data/h2

COPY --from=builder /usr/local/bin/envoy /usr/bin/

USER root

COPY envoy.yaml /etc/envoy/envoy.yaml
RUN chmod go+r /etc/envoy/envoy.yaml && chmod o+w /data/h2

RUN adduser dodex -D

EXPOSE 8088
EXPOSE 8071
EXPOSE 9901

COPY run_dodex.sh /usr/bin
RUN chmod o+x /usr/bin/run_dodex.sh

USER dodex:dodex

RUN mkdir /home/dodex/quarkus && mkdir /home/dodex/quarkus/logs

VOLUME ~

COPY dodex-quarkus-2.10.2-runner.jar /home/dodex/quarkus/dodex-quarkus-2.10.2-runner.jar

WORKDIR /home/dodex/quarkus

ENV JAVA_HOME=/opt/openjdk-17
# make quarkus http run on this address
ENV DOCKER_HOST 0.0.0.0
# Can change to either "postgres" or "mariadb" or override in docker create/run
ENV DEFAULT_DB=h2
ENV USE_HANDICAP=true

USER dodex

CMD source /usr/bin/run_dodex.sh


