FROM registry.access.redhat.com/openjdk/openjdk-11-rhel7 

EXPOSE 8088

USER jboss

RUN mkdir ${HOME}/quarkus
COPY ./build/dodex-quarkus-2.1.0-runner.jar ./quarkus/dodex-quarkus-2.1.0-runner.jar 

WORKDIR ${HOME}/quarkus
CMD /usr/bin/java -jar ./dodex-quarkus-2.1.0-runner.jar

