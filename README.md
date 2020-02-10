# dodex-quarkus

An asynchronous server for Dodex, Dodex-input and Dodex-mess using the Quarkus Supersonic Subatomic Java Framework.

See <https://github.com/DaveO-Home/dodex-quarkus/blob/master/QUARKUS-README.md> for Quarkus details.

## Install Assumptions

1. Java 8 or higher installed with JAVA_HOME set.
2. Gradle 6+ installed. If you have sdkman installed, execute ```sdk install gradle 6.1.1``` otherwise executing gradlew should install gradle.
3. The `npm` javascript package manager installed.

## Getting Started

1. `npm install dodex-quarkus` or download from <https://github.com/DaveO-Home/dodex-quarkus>. If you use npm install, move node_modules/dodex-quarkus to an appropriate directory.
2. `cd <install directory>/dodex-quarkus/src/main/resources/static` and execute `npm install --save` to install the dodex modules.
3. `cd <install directory>/dodex-quarkus` and execute `gradlew quarkusDev`. This should install java dependencies and startup the server in development mode against the default sqlite3 database. In this mode, any modifications to java source will be recompiled(refresh browser page to recompile).
4. Execute url `http://localhost:8089/test` in a browser.
5. You can also run `http://localhost:8089/test/bootstrap.html` for a bootstrap example.
6. Follow instructions for dodex at <https://www.npmjs.com/package/dodex-mess> and <https://www.npmjs.com/package/dodex-input>.

### Operation

1. Execute `gradlew tasks` to view all tasks.
2. Execute `gradlew quarkusBuild --uber-jar` to build the production fat jar.
3. Execute `java -jar build/dodex-quarkus-1.0.0-runner.jar` to startup the production server.
4. Execute url `http://localhost:8088/ddex` or `.../ddex/bootstrap.html` in a browser. __Note;__ This is a different port and url than development. Also __Note;__ The default database on the backend is "Sqlite3", no further configuation is necessay. Dodex-quarkus also has Postgres/Cubrid/Mariadb implementations. See `<install directory>/dodex-quarkus/src/main/resources/database_config.json` for configuration.
5. Swapping among databases; Use environment variable `DEFAULT_DB` by setting it to either `sqlite3` ,`postgres`, `cubrid`, `mariadb` or set the default database in `database_config.json`.
6. When Dodex-quarkus is configured for the Cubrid database, the database must be created using UTF-8. For example `cubrid createdb dodex en_US.utf8`.
7. The dodex server has an auto user clean up process. See `application-conf.json` and `DodexRouter.java` for configuration. It is turned off by default. Users and messages may be orphaned when clients change a handle when the server is offline.

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

## Test

1. Make sure the demo Java-quarkus server is running in development mode.
2. Test Dodex-mess by entering the URL `localhost:3089/test/index.html` in a browser.
3. Ctrl+Double-Click a dial or bottom card to popup the messaging client.
4. To test the messaging, open up the URL in a different browser and make a connection by Ctrl+Double-Clicking the bottom card. Make sure you create a handle.
5. Enter a message and click send to test.
6. For dodex-input Double-Click a dial or bottom card to popup the input dialog. Allows for uploading, editing and removal of private content. Content in JSON can be defined as arrays to make HTML more readable.

## Authors

* *Initial work* - [DaveO-Home](https://github.com/DaveO-Home)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
