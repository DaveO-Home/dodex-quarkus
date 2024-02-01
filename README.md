# dodex-quarkus

An asynchronous server for Dodex, Dodex-input and Dodex-mess using the Quarkus Supersonic Subatomic Java Framework.

## Install Assumptions

1. Java 17+ installed with JAVA_HOME set.
2. Gradle 8.5 installed. If you have sdkman installed, execute `sdk install gradle 8.1.1` otherwise executing gradlew should install gradle.
3. Javascript **node** with **npm** package manager installed.

__Note:__ The static directory was changed from **src/main/resources/static** to **src/main/resources/META-INF/resources** in compliance with v2.5.

## Getting Started

### Quick Getting Started

1. Execute __`docker build -t dufferdo2/dodex-quarkus:latest -f kube/quarkus/Dockerfile ./kube`__
2. Execute __`docker run -d -p 8088:8088 -p 8071:8071 -p 9901:9901 --name dodex_quarkus dufferdo2/dodex-quarkus`__
3. View in browser; __localhost:8088/ddex__ or __localhost:8088/ddex/bootstrap.html__ or __localhost:8088/handicap.html__
4. To verify that the image is working, execute __`docker exec -ti  --tty  dodex_quarkus /bin/sh`__ and then __`cat logs/quarkus.log`__
5. To keep and run later, execute `docker stop dodex_quarkus` and later `docker start dodex_quarkus`
6. To cleanup execute `docker stop dodex_quarkus` and `docker rm dodex_quarkus` and `docker rmi dufferdo2/dodex-quarkus` and `docker rmi envoyproxy/envoy:v1.25.0`

### Building `dodex-quarkus`

1. `npm install dodex-quarkus` or download from <https://github.com/DaveO-Home/dodex-quarkus>. If you use npm install, move node_modules/dodex-quarkus to an appropriate directory.
2. `cd <install directory>/dodex-quarkus/src/main/resources/META-INF/resources` and execute `npm install --save` to install the dodex modules.
3. `cd <install directory>/dodex-quarkus` and execute `gradlew quarkusDev`. This should install java dependencies and startup the server in development mode against the default sqlite3 database. In this mode, any modifications to java source will be recompiled(refresh browser page to recompile).
4. Execute url `http://localhost:8089/test` in a browser.
5. You can also run `http://localhost:8089/test/bootstrap.html` for a bootstrap example.
6. Follow instructions for dodex at <https://www.npmjs.com/package/dodex-mess> and <https://www.npmjs.com/package/dodex-input>.
7. The Cassandra database has been added via an `Akka` micro-service. See; <https://www.npmjs.com/package/dodex-akka>.
8. Added Cassandra database to the **React** demo allowing the **login** component to use Cassandra.
9. See the **Firebase section** for using Google's **Firestore** backend.
10. To generate `jooq` code, set `DEFAUT_DB=sqlite3` and execute `./gradlew jooqGenerate`. The code is generated to `src/main/kotlin/golf/handicap/generated`.

   ___Note:___ In dev mode(`gradlew quarkusDev`), when modifying Java code, all you have to do is refresh the browser window. You can also use `gradlew run`(in build.gradle) to set ENVIRONMENT variables first.

   ___See:___ Single Page React Section below on using Dodex in an SPA.

### Operation

1. Execute `gradlew tasks` to view all tasks.
2. Building the Production Uber jar
    1. Before running the Uber jar for production, do: (graalvm requires the Uber jar)
        * Make sure that the spa react javascript is installed. Execute `npm install` in the `src/spa-react` directory.
        * cd to src/spa-react/devl & execute `gulp prod` or `gulp prd` (bypasses tests) or `npx gulp prod`
        * `npm install` must also populate the `node_modules` directory in `src/main/resources/META-INF/resources`
        * (optional) rm src/spa-react/node_modules (makes a smaller uber jar)
    2. Execute `./gradlew quarkusBuild -Dquarkus.package.type=uber-jar` to build the production fat jar.
        * **Important** When building the **Uber** jar, set **DEFAULT_DB**=h2 or mariadb or postgres and **USE_HANDICAP**=true

3. Execute `java -jar build/dodex-quarkus-3.2.0-runner.jar` to startup the production server.
4. Execute url `http://localhost:8088/ddex/index.html` or `.../ddex/bootstrap.html` in a browser. __Note:__ This is a different port and url than development. Also __Note;__ The default database on the backend is "Sqlite3", no further configuation is necessay. Dodex-quarkus also has Postgres/Cubrid/Mariadb/DB2/H2 implementations. See `<install directory>/dodex-quarkus/src/main/resources/database_config.json` and `<install directory>/generate/src/main/resources` for configuration.
5. Swapping among databases; Use environment variable __`DEFAULT_DB`__ by setting it to either `sqlite3` ,`postgres`, `cubrid`, `mariadb`, `ibmdb2`, `h2`, `cassandra`, `firebase`, `neo4j` or set the default database in `database_config.json`.
6. When Dodex-quarkus is configured for the Cubrid database, the database must be created using UTF-8. For example `cubrid createdb dodex en_US.utf8`.
7. The dodex server has an auto user clean up process. See `application-conf.json` and `DodexRouter.java` for configuration. It is turned off by default. Users and messages may be orphaned when clients change a handle when the server is offline.

__Important Note:__ Since building __`jooq`__ source code, the database configurations from the .../dodex-quarkus/generate project may override the __database_configs__ in the the dodex/spa configurations. When making a change to __`src/main/resources/database_(spa)_config.json`__ also make the change in __`generate/src/main/resources`__

## Java Linting with PMD

* Run `gradlew pmdMain` and `gradlew pmdTest` to verify code using a subset of PMD rules in `dodexstart.xml`
* Reports can be found in `build/reports/pmd`
  
## Single Page React Application to demo Development and Integration Testing

* Integrated in ***Dodex-Quarkus*** at `src/spa-react`
* Documentation <https://github.com/DaveO-Home/dodex-quarkus/blob/master/src/spa-react/README.md>
* Uses ***Sqlite3*** as default backend database
* Router added to `src/main/java/dmo/fs/router/DodexRoutes.java`, see the `init` method.

## Debug

* Executing `gradlew quarkusDev` defaults to debug mode.
* Tested with VSCode, the `launch.json` =
  
```javascript
    {
            "type": "java",
            "name": "Debug (Launch) - Dodex",
            "request": "attach",
            "hostName": "localhost",
            "port": 5005
    }
```

## Test Dodex

1. Make sure the demo **dodex-quarkus** server is running in development mode.
2. Test Dodex-mess by entering the URL **localhost:8089/test/index.html** in a browser.
3. Ctrl+Double-Click a dial or bottom card to popup the messaging client.
4. To test the messaging, open up the URL in a different browser and make a connection by Ctrl+Double-Clicking the bottom card. Make sure you create a handle.
5. Enter a message and click **send** to test.
6. For dodex-input Double-Click a dial or bottom card to popup the input dialog. Allows for uploading, editing and removal of private content. Content in JSON can be defined as arrays to make HTML more readable.

## Native execution with Graalvm

The quarkus documentation can be found at: <https://quarkus.io/guides/building-native-image>

A quick start (Assuming graalvm 21+ is installed and configured with `native-image`): 

__The Quarkus Method:__ Execute `gradlew build -Dquarkus.package.type=native`. The additional arguments are in `application.properties` (quarkus.native.additional-build-args). The build fails with numerious errors.


__The Old Fashion Method:__ Execute the supplied script - `dodexvm17`. This will build an executable named `dmo.fs.quarkus.Server`. This script should work, however it uses the fallback javaVM.

## Docker, Podman and Minikube(Kubernetes)
* Assumes **docker**, **podman** and **minikube** are installed

### Building an *image* and *container* with docker
1. cd to the `dodex-quarkus` install directory
2. make sure **dodex** and the **spa-react** node_modules and application are installed

    * in `src/main/resources/META-INF/resources` execute __`npm install`__
    * in `src/spa-react` execute __`npm install`__
    * startup Quarkus in dev mode - __`gradlew quarkusDev`__
    * in **src/spa-react/devl** execute __`npx gulp prod`__ or __`npx gulp prd`__(does not need dodex-quarkus started)
    * stop the quarkus server - ctrl-c or enter __`q`__
    * build the production uber jar - __`./gradlew quarkusBuild -Dquarkus.package.type=uber-jar`__
      * **Important** When building the **Uber** jar, set **DEFAULT_DB**=h2 or mariadb or postgres and **USE_HANDICAP**=true
    * verify the jar's name - if different than `dodex-quarkus-2.1.0-runner.jar`, change in `./kube/Dockerfile`

3. execute __`cp build/dodex-quarkus-2.1.0-runner.jar`__ to **./kube**
4. execute __`docker build -t dufferdo2/dodex-quarkus:latest -f kube/Dockerfile ./kube`__
5. execute __`docker create -t -p 8088:8088 -p 8071:8071 -p 9901:9901 --name dodex_quarkus dufferdo2/dodex-quarkus`__
6. execute __`docker start dodex_quarkus`__
7. use browser to view - <http://localhost:8088/handicap.html> or <http://localhost:8088/ddex> or <http://localhost:8088/ddex/bootstrap.html>, if the spa-react was installed this link should work, <http://localhost:8088/dist/react-fusebox/appl/testapp.html>
8. execute __`docker stop dodex_quarkus`__
9. to clean-up execute __`docker rm dodex_quarkus`__ and __`docker rmi dodex-quarkus`__. However you should keep the **dufferdo2/dodex-quarkus** image if trying out **podman** or **minikube**.
10. to pull and generate a local image from the docker hub, execute __`docker build -t dodex-quarkus:latest -f kube/quarkus/Dockerfile .`__
11. you can also build/run dodex-quarkus(image) and dodex_quarkus(container) with; __`docker compose -f kube/docker-compose.yaml up -d`__ 
12. Use `run` to test different databases; __`docker run --rm -p 8088:8088 -p 8071:8071 -p 9901:9901 -e DEFAULT_DB=postgres -e USE_HANDICAP=true --name dodex_quarkus dufferdo2/dodex-quarkus`__. To stop, run `docker container stop dodex_quarkus`.

   __Note:__ When running a dufferdo2/dodex-quarkus image, there is no need to have **envoy** running on the machine. Envoy is included in the image.

### Building an *image* and *container* with podman
1. generate an empty pod execute __`podman pod create -n quarkus-pod -p 0.0.0.0:8088:8088 -p 0.0.0.0:8071:8071 -p 9901:9901`__
2. generate a container execute __`podman create -t --pod quarkus-pod --name quarkus_server dufferdo2/dodex-quarkus:latest`__.
3. start the container execute __`podman start quarkus_server`__
4. view in browser
5. to clean-up execute __`podman stop quarkus_server`__, __`podman rm quarkus_server`__, __`podman pod rm quarkus-pod`__
6. before cleaning up, you can generate a yaml template. Execute __`podman generate kube quarkus_pod > quarkus.yaml`__

### Building a *deployment*, *service* and *persistent volume* with minikube
   * Since including the **Handicap** application(multiple exposed ports, persistent volume) to **dodex-quarkus**, the **minikube** deployment must be from configuration files.
1. execute __`minikube start`__
2. edit kube/quarkus.yml and change **env:** to desired database(DEFAULT_DB) - defaults to **h2**(embedded), no database configuration necessary otherwise set **DEFAULT_DB** to **mariadb** or **postgres**
3. execute `kubectl create -f kube/h2-volume.yml`
4. execute `kubectl create -f kube/quarkus.yml`
5. execute `minikube service quarkus-service` to start **dodex-quarkus** in the default browser - add **--url** to get just the URL
6. verify that **dodex-quarkus** started properly - execute `./execpod` and `cat ./logs/quarkus.log` - enter `exit` to exit the pod
   
For postgres make sure postgres.conf has entry:

```
             listen_addresses = '*'          # what IP address(es) to listen on;
```                
and  pg_hba.conf has entry:

```
             host    all    all    <ip from minikube vertx-service --url>/32   <whatever you use for security> (default for dodex-vertx "password")
```                   
and database_config.json(also in ../dodex-vertx/generate...resources/database(_spa)_confg.json) entry: postgres... (both dev/prod)
```          
              "config": {
              "host": "<ip value from `hostname -i`>",
```
`netstat -an |grep 5432` should look like this

```
             tcp        0      0 0.0.0.0:5432            0.0.0.0:*               LISTEN     
             tcp6       0      0 :::5432                 :::*                    LISTEN     
             unix  2      [ ACC ]     STREAM     LISTENING     57905233 /var/run/postgresql/.s.PGSQL.5432
             unix  2      [ ACC ]     STREAM     LISTENING     57905234 /tmp/.s.PGSQL.5432
```

#### Development
1. Make changes to the **dodex-quarkus** code
2. execute `gradlew clean`
3. build the **uber** jar and **image** as described in the **Operation** and **Building an *image* and *container* with docker** sections, e.g.
   * build the production uber jar - __`./gradlew quarkusBuild -Dquarkus.package.type=uber-jar`__
     * **Important** When building the **Uber** jar, set **DEFAULT_DB**=h2 or mariadb or postgres and **USE_HANDICAP**=true
     * verify the jar's name - if different than **dodex-quarkus-2.1.0-runner.jar**, change in **./kube/Dockerfile**
   * copy the build/**dodex-quarkus-2.1.0-runner.jar** to **./kube**
   * if the **dodex_quarkus** and/or the **dufferdo2/dodex-quarkus** exist, remove them `docker rm dodex_quarkus` and `docker rmi dufferdo2/dodex-quarkus`
   * build the image `docker build -t dufferdo2/dodex-quarkus:latest -f ./kube/Dockerfile ./kube`
4. execute `./deleteapp`
5. execute `minikube image rm dufferdo2/dodex-quarkus`
6. execute `minikube load image dufferdo2/qodex-quarkus`
7. execute `kubectl create -f kube/quarkus.yml`
8. execute `minikube service quarkus-service`
9. clean-up execute __`./deleteapp`__, __`kubectl delete pvc quarkus-pvc`__, __`kubectl delete pv quarkus-pv`__
10. execute __`minikube stop`__

#### Exposing the minikube **dodex-quarkus** container to the internet
1. cd .../dodex-quarkus and execute **`npm install`** - this will install **localtunnel**
2. execute `minikube service quarkus-service --url` to view the local host **ip** address - can be used for the **--local-host** value
3. in separate terminals 
   * execute `npx localtunnel  --host https://localtunnel.me --subdomain my-app --port 30088 --local-host $(minikube service quarkus-service --url | cut -d":" -f2 | cut -d"/" -f3)` 
   * for the gRPC tunnel, execute `npx localtunnel  --host https://localtunnel.me --subdomain my-app2 --port 30071 --local-host $(minikube service quarkus-service --url | cut -d":" -f2 | cut -d"/" -f3)`
     * the **--subdomain** for **my-app** and **my-app2** should be changed to unique values
     * the naming convention is required(otherwise edit src/grpc/client/js/client.js and tweak) e.g. **coolapp** for port 30088 and **coolapp2** for port 30071
   * view <https://YOUR-UNIQUE-APP.loca.lt> or <https://YOUR-UNIQUE-APP.lt/handicap.html> in browser
   
   __Note:__ Make sure your Ad-Blocker is turned off for the web site.

### Firebase

* Create an account: <https://firebase.google.com>
* Getting started: <https://firebase.google.com/docs/admin/setup#java>
* Make sure you create a `Service-Account-Key.json` file as instructed. Dodex-Vertx uses the environment variable option to set the service-account - `GOOGLE_APPLICATION_CREDENTIALS`. See gradle.build as one way to set it.
* You will need to login to the `Firebase` console and create the `dodex-firebase` project. See `src/main/java/dmo/fs/router/FirebaseRouter.java` for usage of the project-id and Google Credentials. __Note:__ The `Firebase` rules are not used, so they should be set to `allow read, write:  if false;` which may be the default.
* You only need the `Authentication` and `Firestore` extensions.
* If you want a different project name, change `.firebaserc`.
* Gradle for development can set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable if you exec `gradlew run` instead of `gradlew quarkusDev`. Don't forget to modify the build.gradle file with the location of your  `Service-Account-Key.json` file.

 #### Firebase Testing

  * To make sure your project is created and the setup works, you should run the tests. __Note:__ They are written in Typescript.
  * cd `../dodex-quarkus/src/firebase` and run `npm install`
  * execute `npm run emulators` to startup the emulators for testing.
  * To test the model and rules after starting the emulators, in a different terminal window, run `npm test`.

### Neo4j

* See <http://quarkus.io/guides/neo4j> for usage.
* To use a docker with `apoc` you can try: __Note:__ this has `--privileged` set.
    ```
    docker run \
    -p 7474:7474 -p 7687:7687 \
    -v $PWD/neo4j/data:/neo4j/data -v $PWD/neo4j/plugins:/neo4j/plugins \
    --name neo4j-apoc \
    --privileged \
    -e 'NEO4J_AUTH=neo4j/secret' \
    -e NEO4J_apoc_export_file_enabled=true \
    -e NEO4J_apoc_import_file_enabled=true \
    -e NEO4J_apoc_import_file_use__neo4j__config=true \
    -e NEO4JLABS_PLUGINS=\[\"apoc\"\] \
    -e NEO4J_dbms_security_procedures_unrestricted=apoc.\\\* \
    neo4j:4.3
    ```
To restart and stop: `docker start neo4j-apoc` and `docker stop neo4j-apoc`

The Neo4j was tested with the `apoc` install, however the database should work without it.

Simply execute `export DEFAULT_DB=neo4j` to use, after database setup.

### Dodex Monitoring

#### Getting Started

* Apache Kafka must be installed.
    *  [Kafka Quickstart](https://kafka.apache.org/quickstart) - A container should also work
    *  .../config/server.properties should be modified if using a local install
        * advertised.listeners=PLAINTEXT://localhost:9092
        * num.partitions=2   # at least 2
    * local startup
        *  ./bin/zookeeper-server-start.sh config/zookeeper.properties
        *  ./bin/kafka-server-start.sh config/server.properties

* Setup Quarkus for Kafka
    *  __set environment variable `DODEX_KAFKA=true`__
    * Modify Quarkus application.properties file
        *  __uncomment the `mp.messaging` entries__
        *  modify the server entries if necessary
    *  startup Quarkus - the monitor should work with any of the databases
    *  the monitor configuation can be found in `application-conf.json`

* Monitor Dodex
    * in a browser enter `localhost:8089/monitor` or `localhost:8088/monitor` in production.
    * as dodex messaging executes the events should be recorded.
    * in the browser's `developer tools` console execute `stop();` and `start();` to stop/start the polling. Polling is started by default.
    
    __Note:__ you can open the messaging dialog with `ctrl-doubleclick` on the dials

## Dodex Groups using OpenAPI

* A default javascript client is included in __.../dodex-quarkus/src/main/resources/static/group/__. It can be regenerated in __.../dodex-quarkus/handicap/src/grpc/client/__ by executing __`npm run group:prod`__.
* The group javascript client is in __.../src/grpc/client/js/dodex/groups.js__ and __group.js__.  
  __Note:__ The client is included in the __Handicap__ application by default.
* See __.../src/main/resources/META-INF/openapi.yaml__ for OpenAPI declarations. You and view the configuration for development at __`localhost:8089/q/swagger-ui/`__.
* The implementation uses a `rest` api in the `OpenApiRouter` class.

### Installing in Dodex
1. Implementing in a javascript module; see __.../dodex-quarkus/handicap/src/grpc/client/js/dodex/index.js__
    * `import { groupListener } from "./groups";`
    * in the dodex init configuration, add
    ```
   ...
    .then(function () {
         groupListener();
   ...
    ```
2. Implementing with inline html; see __.../dodex-quarkus/main/resources/test/index.html__
    * `<script src="../group/main.min.js"></script>`
    * in the dodex init configuration, add
    ```
   ...
    .then(function () {
         window.groupListener();
   ...
    ```
3. Using dodex-messaging group functionality  
   __Note:__ Grouping is only used to limit the list of "handles" when sending private messages.
* Adding a group using `@group+<name>`
    * select __Private Message__ from the __more__ button dropdown to get the list of handles.
    * enter `@group+<name>` for example `@group+aces`
    * select the handles to include and click "Send". Members can be added at any subsequent time.
* Removing a group using `@group-<name>`
    * enter `@group-<name>` for example `@group-aces` and click "Send". Click the confirmation popup to complete.
* Removing a member
    * enter `@group-<name>` for example `@group-aces`
    * select a "handle" from the dropdown list and click "Send"
* Selecting a group using `@group=<name>`
    * enter `@group=<name>` for example `@group=aces` and click "Send"
    * Select from reduced set of "handles" to send private message.

__Note:__ By default the entry `"dodex.groups.checkForOwner"` in __application-conf.json__ is set to __false__. This means that any "handle" can delete a "group" or "member". Setting the entry to __true__ prevents global administration, however, if the owner "handle" changes, group administration is lost.  
        Also, Groups will work with "sqlite3" without setting USE_HANDICAP=true. For 'h2', 'mariadb' and 'postgres', USE_HANDICAP=true is required.


### Kotlin, gRPC Web Application

* This web application can be used to maintain golfer played courses and scores and to calculate a handicap index. The application has many moving parts from the ___envoy___ proxy server to ___kotlin___, ___protobuf___, ___gRPC___, ___jooq___ and __code generator__, ___bootstrap___, ___webpack___, ___esbuild___, ___gradle___, ___java___ and ___javascript___.

  See documentation at: <https://github.com/DaveO-Home/dodex-quarkus/blob/master/handicap/README.md>

## ChangeLog

<https://github.com/DaveO-Home/dodex-quarkus/blob/master/CHANGELOG.md>

## Authors

* *Initial work* - [DaveO-Home](https://github.com/DaveO-Home)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
