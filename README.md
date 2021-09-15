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
   ___Note:___ In dev mode(`gradlew quarkusDev`), when modifying Java code, all you have to do is refresh the browser window.

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
1. Swapping among databases; Use environment variable __`DEFAULT_DB`__ by setting it to either `sqlite3` ,`postgres`, `cubrid`, `mariadb`, `ibmdb2`, `h2` or set the default database in `database_config.json`.
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

## ChangeLog

<https://github.com/DaveO-Home/dodex-quarkus/blob/master/CHANGELOG.md>

## Authors

* *Initial work* - [DaveO-Home](https://github.com/DaveO-Home)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
