# dodex-quarkus

An asynchronous server for Dodex, Dodex-input and Dodex-mess using the Quarkus Supersonic Subatomic Java Framework.

## Install Assumptions

1. Java 11 or higher installed with JAVA_HOME set.
2. Gradle 6+ installed. If you have sdkman installed, execute ```sdk install gradle 6.9``` otherwise executing gradlew should install gradle.
3. The `npm` javascript package manager installed.

## Getting Started

1. `npm install dodex-quarkus` or download from <https://github.com/DaveO-Home/dodex-quarkus>. If you use npm install, move node_modules/dodex-quarkus to an appropriate directory.
2. `cd <install directory>/dodex-quarkus/src/main/resources/static` and execute `npm install --save` to install the dodex modules.
3. `cd <install directory>/dodex-quarkus` and execute `gradlew quarkusDev`. This should install java dependencies and startup the server in development mode against the default sqlite3 database. In this mode, any modifications to java source will be recompiled(refresh browser page to recompile).
4. Execute url `http://localhost:8089/test` in a browser.
5. You can also run `http://localhost:8089/test/bootstrap.html` for a bootstrap example.
6. Follow instructions for dodex at <https://www.npmjs.com/package/dodex-mess> and <https://www.npmjs.com/package/dodex-input>.
7. The Cassandra database has been added via an `Akka` micro-service. See; <https://www.npmjs.com/package/dodex-akka>.
8. Added Cassandra database to the `React` demo allowing the `login` component to use Cassandra.
9. See the `Firebase` section for using Google's `Firestore` backend.

   ___Note:___ In dev mode(`gradlew quarkusDev`), when modifying Java code, all you have to do is refresh the browser window. You can also use `gradlew run` to set ENVIRONMENT variables first.

    ___See:___ Single Page React Section below on using Dodex in an SPA.

### Operation

1. Execute `gradlew tasks` to view all tasks.
1. Building the Production Uber jar
    1. Before running the Uber jar for production, do: (graalvm requires the Uber jar)
        * Make sure that the spa react javascript is installed. Execute `npm install` in the `src/spa-react` directory.
        * cd to src/spa-react/devl & execute gulp prod or gulp prd (bypasses tests)
        * ~~rm src/main/resources/static/node_modules (makes a smaller uber jar)~~
    1. Execute `./gradlew quarkusBuild -Dquarkus.package.type=uber-jar` to build the production fat jar.

    ~~__Note__; The `node_modules` directory can be re-added by executing `npm install` in `src/main/resources/static`.~~ However, the node_modules directory can be removed when running `graalvm`.

1. Execute `java -jar build/dodex-quarkus-2.1.0-runner.jar` to startup the production server.
1. Execute url `http://localhost:8088/ddex` or `.../ddex/bootstrap.html` in a browser. __Note;__ This is a different port and url than development. Also __Note;__ The default database on the backend is "Sqlite3", no further configuation is necessay. Dodex-quarkus also has Postgres/Cubrid/Mariadb/DB2/H2 implementations. See `<install directory>/dodex-quarkus/src/main/resources/database_config.json` for configuration.
1. Swapping among databases; Use environment variable __`DEFAULT_DB`__ by setting it to either `sqlite3` ,`postgres`, `cubrid`, `mariadb`, `ibmdb2`, `h2`, `cassandra`, `firebase` or set the default database in `database_config.json`.
1. When Dodex-quarkus is configured for the Cubrid database, the database must be created using UTF-8. For example `cubrid createdb dodex en_US.utf8`.
1. The dodex server has an auto user clean up process. See `application-conf.json` and `DodexRouter.java` for configuration. It is turned off by default. Users and messages may be orphaned when clients change a handle when the server is offline.

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

1. Make sure the demo Java-quarkus server is running in development mode.
2. Test Dodex-mess by entering the URL `localhost:8089/test/index.html` in a browser.
3. Ctrl+Double-Click a dial or bottom card to popup the messaging client.
4. To test the messaging, open up the URL in a different browser and make a connection by Ctrl+Double-Clicking the bottom card. Make sure you create a handle.
5. Enter a message and click send to test.
6. For dodex-input Double-Click a dial or bottom card to popup the input dialog. Allows for uploading, editing and removal of private content. Content in JSON can be defined as arrays to make HTML more readable.

## Native execution with Graalvm

The quarkus documentation can be found at: <https://quarkus.io/guides/building-native-image>

A quick start (Assuming graalvm 21+ is installed and configured with `native-image`): 

__The Quarkus Method:__ `gradlew quarkusBuild -Dquarkus.package.type=native -Dquarkus.native.additional-build-args=--initialize-at-run-time=dmo.fs.spa.router.SpaRoutes` __Note;__ This will build an executable in the `build` directory named `dodex-quarkus-2.1.0-runner`. Currently there is a problem with loading the `database_config.json` file. 

__The Old Fashion Method:__ Execute the supplied script - `dodexvm11`. This will build an executable named `dmo.fs.quarkus.Server`. This executable should work.

## Docker, Podman and Minikube(Kubernetes)

* Assumes `docker`, `podman` and `minikube` are installed

1. ### Building an __`image`__ and __`container`__ with `docker`
    1. cd to the `dodex-quarkus` install directory
    1. make sure `dodex` and the `spa-react` node_modules and application are installed

        * in `src/main/resources/static` execute __`npm install`__
        * in `src/spa-react` execute __`npm install`__
        * startup Quarkus in dev mode - __`gradlew quarkusDev`__
        * in `src/spa-react/devl` execute __`gulp prod`__ or __`gulp prd`__
        * stop the quarkus server - ctrl-c or enter __`q`__
        * build the production uber jar - __`./gradlew quarkusBuild -Dquarkus.package.type=uber-jar`__
        * verify the jar's name - if different than `dodex-quarkus-2.1.0-runner.jar`, change in `./kube/Dockerfile`

    1. execute __`docker build -t dodex-quarkus:latest -f kube/Dockerfile .`__
    1. execute __`docker create -t -p 8088:8088 --name dodex_quarkus dodex-quarkus`__
    1. execute __`docker start dodex_quarkus`__
    1. use browser to view - <http://localhost:8088/ddex> or <http://localhost:8088/ddex/bootstrap/html>, if the spa-react was installed this link should work, <http://localhost:8088/dist/react-fusebox/appl/testapp.html>
    1. execute __`docker stop dodex_quarkus`__
    1. to clean-up execute __`docker rm dodex_quarkus`__ and __`docker rmi dodex-quarkus`__
    1. to pull and generate a local image from the docker hub, __`execute docker build -t dodex-quarkus:latest -f kube/quarkus/Dockerfile .`__

1. ### Building an __`image`__ and __`container`__ with `podman`
    1. generate an empty pod execute __`podman pod create -n quarkus-pod -p 0.0.0.0:8088:8088`__
    1. generate a container execute __`podman create -t --pod quarkus-pod --name quarkus_server dodex-quarkus:latest`__ __Note;__ if there is not a local image, use `dufferdo2/dodex-quarkus:latest` to pull from the docker hub.
    1. start the container execute __`podman start quarkus_server`__
    1. view in browser
    1. to clean-up execute __`podman stop quarkus_server`__, __`podman rm quarkus_server`__, __`podman pod rm quarkus-pod`__
    1. before cleaning up, generate a yaml file for `minikube`, execute __`podman generate kube quarkus_pod > quarkus.yml`__

1. ### Building a __`deployment`__ and __`service`__ with `minikube`
* `minikube` can be forced to pull from a local registry, however for this exercise `minikube` pulls from the docker hub, `dufferdo/dodex-quarkus`

    1. execute __`minikube start`__
    1. create a deployment with auto generated pod, execute __`kubectl create deployment quarkus-depl --image=dufferdo2/dodex-quarkus:latest`__
    1. create a service from deployment, execute __`kubectl expose deploy quarkus-depl --name=quarkus-service --port 8088 --target-port 8088 --type=NodePort`__
    1. to find the generated pod name, execute __`kubectl get pod`__
    1. to run in default browser, execute __`minikube service quarkus-service`__
    1. to get the ip:port to use, execute __`minikube service quarkus-service --url`__
    1. view in browser
    1. clean-up execute __`kubectl delete svc quarkus-service`__, __`kubectl delete deploy quarkus-depl`__, __`docker rmi dufferdo/dodex-quarkus`__
    1. execute __`minikube stop`__

    __Note;__ From the the above `quarkus.yml` file, a pod can be created, execute __`kubectl create -f quarkus.yml`__ and the service __`kubectl expose po quarkus-pod --name=quarkus-service --port 8088 --target-port 8088 --type=NodePort`__. Make sure the image entry in `quarkus.yml` is `image: dufferdo2/dodex-quarkus:latest`. Optionally add the following after `image:...` -  `imagePullPolicy: IfNotPresent`. If not working, try __`kubectl port-forward svc/quarkus-service 8088:8088`__ and view with `localhost:8088`. Depending on your setup, the following may be needed; __`eval $(minikube -p minikube docker-env)`__

### Firebase

* Create an account: <https://firebase.google.com>
* Getting started: <https://firebase.google.com/docs/admin/setup#java>
* Make sure you create a `Service-Account-Key.json` file as instructed. Dodex-Vertx uses the environment variable option to set the service-account - `GOOGLE_APPLICATION_CREDENTIALS`. See gradle.build as one way to set it.
* You will need to login to the `Firebase` console and create the `dodex-firebase` project. See `src/main/java/dmo/fs/router/FirebaseRouter.java` for usage of the project-id and Google Credentials. __Note;__ The `Firebase` rules are not used, so they should be set to `allow read, write:  if false;` which may be the default.
* You only need the `Authentication` and `Firestore` extensions.
* If you want a different project name, change `.firebaserc`.
* Gradle for development can set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable if you exec `gradlew run` instead of `gradlew quarkusDev`. Don't forget to modify the build.gradle file with the location of your  `Service-Account-Key.json` file.

 #### Testing

  * To make sure your project is created and the setup works, you should run the tests. __Note;__ They are written in Typescript.
  * cd `../dodex-vertx/src/firebase` and run `npm install`
  * execute `npm run emulators` to startup the emulators for testing.
  * To test the model and rules after starting the emulators, in a different terminal window, run `npm test`.

## ChangeLog

<https://github.com/DaveO-Home/dodex-quarkus/blob/master/CHANGELOG.md>

## Authors

* *Initial work* - [DaveO-Home](https://github.com/DaveO-Home)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
