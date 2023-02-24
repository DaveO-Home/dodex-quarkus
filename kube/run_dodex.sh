#!/bin/sh

nohup /usr/bin/envoy --enable-fine-grain-logging --base-id 2  -l info -c /etc/envoy/envoy.yaml &

${JAVA_HOME}/bin/java -jar /home/dodex/quarkus/dodex-quarkus-2.10.2-runner.jar
