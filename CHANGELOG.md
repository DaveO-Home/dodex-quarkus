# Changelog

## [v3.1.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v3.1.0) (2023-06-03)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v3.0.0...v3.1.0)

* Upgraded Quarkus to 3.1.1 - includes kotlin 1.8.21
* Upgraded Gradle to 8.1.1
* Upgraded javascript dependencies(firebase, spa-react, dodex, grpc/client)
* Fixed quarkus deprecation for `console.color`, moved to application-conf.json
* Fixed font size on handicap form.

## [v3.0.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v3.0.0) (2023-05-09)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.6.3...v3.0.0)

* Upgraded Quarkus to 3.0.2
* Upgraded dependencies Java(build.gradle), Javascript(package.json files)
* Code Changes
  * javax to jakarta packages (javax.websocket, javax.enterprise etc)
  * Mutiny reactive changes for neo4j to make compliant with neo4j jdbc driver 5
* Added dodex content to handicap application

## [v2.6.3](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.6.3) (2023-03-08)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.6.2...v2.6.3)

* Upgraded Quarkus to 2.16.4
* Fixed CORS configuration for v2.16 - application.properties
* Javascript updates - grpc, react, firebase, dodex
* Upgraded java dependencies

## [v2.6.2](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.6.2) (2023-02-28)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.6.1...v2.6.2)

* Upgraded Quarkus to 2.15.3
* Native windows setup for the handicap application with envoy - see "Using on native Windows" in ../handicap/README.md
* Major upgrade to the docker/minikube configuration for the dufferdo2/dodex-quarkus image
  * Image includes envoy
  * Minikube envoy/handicap inclusion
  * Minikube persistent volume for h2(embedded)
  * Minikube exposed to internet using `localtunnel`(javascript) tunnel with static subdomains(very cool)
* Upgraded gradle to 7.6, supports java17/18/19
* Upgraded firebase client
* Changes to README.md

## [v2.6.1](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.6.1) (2023-01-14)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.6.0...v2.6.1)

* Changed src/grpc/client/proto script to run out of the box after npm install. Using the node version of protoc.
* For some reason git will not recognize the protoc generated code.

## [v2.6.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.6.0) (2023-01-12)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.5.2...v2.6.0)

* Upgraded quarkus dependencies - quarkus -> 2.15 gradle -> 7.5
* Upgraded all javascript dependencies, including -> React 18.2.0(many breaking changes)
* Made numerous changes to jasmine tests related to react testing
* Changed front end layout based on Bootstrap 5.2.3 (spa app)
* Added a Kotlin Golf Handicap Application using Grpc, protobuf, javascript client(esbuild/webpack),
  jooq code generator, envoy and gradle server-side protobuf generator(quarkus internal).

## [v2.5.2](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.5.2) (2022-05-02)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.5.1...v2.5.2)

* Upgraded quarkus dependencies - quarkus -> 2.8.2 gradle -> 7.3.3
* Upgraded spa web app dependencies
* Upgraded dodex dependencies

## [v2.5.1](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.5.1) (2022-01-06)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.5.0...v2.5.1)

* Fixed moniter to run in production
* Some code cleanup based on PMD linting
* Changed dodexvm11 to generate graalvm executable
* Working on Quarkus method for graalvm - build issues...unsupported features

## [v2.5.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.5.0) (2022-01-04)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.4.0...v2.5.0)

* Upgraded Quarkus to v2.6.1
* Added dodex monitoring tool - see README.md
* Added kafka to facilitate monitoring
* Fixed removing session data on close

## [v2.4.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.4.0) (2021-12-13)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.3.0...v2.4.0)

* Upgraded Quarkus to v2.5.2
* Moved resources/static/* to resources/META-INF/resources in compliance with v2.5 - `dodex` install is now at this location
* Made modifications in routes and spa-react/devl to reflect static location change
* Added Neo4j database - See README.md

## [v2.3.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.3.0) (2021-11-01)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.2.0...v2.3.0)

* Upgraded Quarkus to v2.4.1
* Fixed connection issue with sqlite/h2 connection caused by cubrid override

## [v2.2.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.2.0) (2021-11-01)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.1.0...v2.2.0)

* Upgraded Quarkus to v2.3.1
* Upgraded the React demo app dependencies
* Fixed Cubrid null pointer with work around(query generated key)

## [v2.1.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.1.0) (2021-10-06)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.0.2...v2.1.0)

* Added Cassandra database via an `Akka` microservice, See; <https://www.npmjs.com/package/dodex-akka>.
* Added Firebase(Firestore) database
* Converted `mjson.Json` to vertx `JsonObject`
* Added Cassandra/Firebase login in React SPA demo.
* Upgraded to Quarkus 2.2.3 and Scala Sbt to 1.5.5
* Added TcpEventBusBridge to communicate with `Akka`- see; DodexRoutes.java

## [v2.0.2](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.0.2) (2021-09-17)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.0.1...v2.0.2)

* Added docker/podman/minikube - see README and ./kube directory
* Added host to server startup - needed if using across multiple machines
* Added "static" to a few more methods in DbReactiveSqlBase.java & src/main/java/dmo/fs/db/DbDefinitionBase.java

## [v2.0.1](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.0.1) (2021-09-15)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v2.0.0...v2.0.1)

* Striked bad instructions in README
* Made the dodex websocket more general - should work with any ip/host without manual changes 

## [v2.0.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v2.0.0) (2021-09-07)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v1.3.0...v2.0.0)

* Upgraded to Quarkus 2.2.2
* Converted to reactivex and mutiny
* Upgraded React dependencies
* Upgraded Dodex javascript

## [v1.3.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v1.3.0) (2021-04-19)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v1.2.2...v1.3.0)

* Upgraded to Quarkus 1.13.2
* Added route to properly load markdown document. Static handler did not.
* Determine environment the Quarkus way for dev/prod.
* Removed twitter deprecated timeline on react dodex example.
* Changed React Login form from GET to POST.
* Upgraded React dependencies to 0 vulnerabilties.
* Upgraded Karma Server configuration.

## [v1.2.2](https://github.com/DaveO-Home/dodex-quarkus/tree/v1.2.2) (2020-07-03)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v1.2.1...v1.2.2)

* No longer a need to manually restart the Quarkus server when developing javascript
* removed blocking code when removing undelivered messages.
* Fixed websocket port for production with fat jar.

## [v1.2.1](https://github.com/DaveO-Home/dodex-quarkus/tree/v1.2.1) (2020-06-29)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v1.2.0...v1.2.1)

* Fixed Dodex exception when messaging multiple private users
* Removed puppeteer - install(npm install puppeteer) and uncomment in gulpfile.js to use.
* Upgraded to Quarkus 1.5.2-Final

## [v1.2.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v1.2.0) (2020-06-12)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/v1.1.0...v1.2.0)

* Added React SPA application
* Added Java PMD linting
* Upgraded Gradle to 6.5

## [v1.1.0](https://github.com/DaveO-Home/dodex-quarkus/tree/v1.1.0) (2020-06-08)

[Full Changelog](https://github.com/DaveO-Home/dodex-vertx/compare/bc4d7b71f3edb5b7be73ea0e13c1d1f7ed525ea1...v1.1.0)

* Added Change Log
* Changed database access to non-blocking(major code changes)
* Fixed Dev detection for database config

\* *This Changelog was automatically generated by [github_changelog_generator](https://github.com/github-changelog-generator/github-changelog-generator)*
