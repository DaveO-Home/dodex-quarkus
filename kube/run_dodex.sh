#!/bin/sh

nohup /usr/bin/envoy --enable-fine-grain-logging --base-id 2  -l info -c /etc/envoy/envoy.yaml &

${JAVA_HOME}/bin/java -jar `pwd`/dodex-quarkus/build/dodex-handicap-3.17.0-runner.jar
