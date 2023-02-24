
### Kotlin, gRPC Web Application

### Getting Started

#### Client:

* The __html/javascript__ client is generated from the __.../dodex-quarkus/src/grpc/client__ directory. 
    * Execute __`npm install`__ to download the javascript dependencies.

    * When changing the client proto configuration, execute `./proto protos/handicap` to generate the `proto3/gRPC` javascript modules. The proto3 configuration is in `./protos/handicap.proto`.
      
      __Note:__ Executing __`./proto protos/handicap`__ before building the client is optional if the proto configuration has not changed.

    * Execute either `npm run esbuild:build` or `npm run webpack:build` to package the javascript development client. The output is located in **src/main/resources/META-INF/resources/**. When making changes to javascript, html or css, simply rerun `npm run esbuild:build`, if the verticle is running, refresh the browser. For proto3 changes, rerun `./proto protos/handicap` first.

        __Note:__ **esbuild** is best for development(very fast) and **webpack** is better for production, e.g. `npm run webpack:prod`.

#### Server:

* The server **proto3/gRPC** classes are auto generated from Quarkus. The **proto3** configuration is in the  __src/main/proto__ directory. __Note:__ The client and server proto3 configurations should be identical.

* From the dodex-quarkus directory, execute `gradlew quarkusDev`. The **jooqGenerate** task executes the **jooq** code generator. The generated code can be found in **handicap/src/main/kotlin/golf/handicap/generated**. Objects can then be used to define the queries.

    __Note__: The jooq generated code is included with the install. No need to execute the `jooqGenerate` task unless database tables change. When executing the `jooqGenerate` task, either unset __DEFAULT_DB__ or execute __`export DEFAULT_DB=postgres`__ first.

* The next step is to install/startup the __envoy__ proxy server. The javascript client needs a proxy to convert **http/1* to *http/2* etc. Assuming **envoy** is installed <https://www.envoyproxy.io/docs/envoy/latest/start/install>, execute the `start.envoy` script in the **dodex-quarkus**` directory. The configuration for the proxy server is in **.../dodex-quarkus/handicap/handicap.yaml**.

* If all goes well, only dodex-quarkus at port **8089** should be started, the **handicap** application is turned off by default. 

  __Note:__ By default, **enableHandicap** is set to `true` in **.../resources/application-conf.json**, however to allow **handicap** to start by default, comment this line __@IfBuildProperty(name = "USE_HANDICAP", stringValue = "true")__ in __.../dodex-quarkus/src/main/kotlin/golf/handicap/routes/GrpcRoutes.kt__.

* Kill **dodex-quarkus**(ctrl-c) or enter __q__, and set the environment variable `export USE_HANDICAP=true` and restart the server. You should get the yellow display __Handicap Started on port: 8089__.

* In a browser enter **localhost:8089/handicap.html**.

* The frontend html/gRPC javascript client should display. See operation section on how to use the form.
    
    __Note:__ Only **h2**, **mariadb** and **postgres** support the handicap application. 
    
    * The default database is **sqlite3**. To change the default database, execute `export DEFAULT_DB=h2`, `export DEFAULT_DB=mariadb` or `export DEFAULT_DB=postgres`. The `H2` database in dev does not require configuration.
    * Also, if gradle generates the error "BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_' Unsupported class file major version 63", change the **jdk** to **java19** and rerun. Afterwards, the **jdk** can be set back to say **java20**.

### Production Build

* In `dodex-quarkus/src/grpc/client` execute `npm run webpack:prod`, `esbuild:prod` also works.
* Before building the **uber** jar, execute `export USE_HANDICAP=true` and `export DEFAULT_DB=h2`
* In `dodex-quarkus` execute `./gradlew quarkusBuild -Dquarkus.package.type=uber-jar`
* To start the production verticle, execute `java -jar build/dodex-quarkus-2.10.2-runner.jar`
* Execute a browser with **url** http://localhost:8088/handicap.html

Upgraded quarkus dependencies - quarkus -> 2.15.3 gradle -> 7.6
  __Note:__ Make sure **dodex** is installed before building the uber-jar. In __src/main/resources/META-INF/resources__, execute `npm install`.

### Operation

The following are the steps to add golfer info, courses with tee data and golfer scores.

* First time login simply enter a **pin**(2 alpha characters with between 4-6 addition alpha-numeric characters) with first and last name. Click the login button to create/login to the application. On subsequent logins only the `pin` is needed, don't forget it. The **Country** and **State** should also be selected before creating a *pin* as default values. However, you can change the defaults on any login. Also, **Overlap Years** and **Public** should be set.

    * Overlap will use the previous year's scores for the handicap if needed.
    * Public will allow your scores to show up in the **Scores** tab for public viewing.
    
* Add a course by entering it's name with one radio button selected for the tee. You can also change the tee's color. The **rating**, **slope** and **par** values are also required. Click the **Add Tee** button. After the first added tee, the others can be added when needed.

__Note:__: You can disable the course/tee add function by setting **handicap.enableAdmin** to **true** in the **...\resources\application-conf.json** file. And then use the default **admin.pin** to administer the courses/tees. When using this pin, a first and last name must be entered on initial use.

* To add a score, select a course and tee with values for **Gross Score**, **Adjusted Score** and **Tee Time**. Click the **Add Score** button to add the score. The **Remove Last Score** will remove the last entered score, multiple clicks will remove multiple scores.

__Note:__ A handicap will be generated after 5 scores have been added.

### Using on native Windows

You can run Dodex-Quarkus in a native Windows __cmd__ terminal using `.\gradlew quarkusDev`.

* Install `node` for Windows.
* Install docker for Windows - `Docker Desktop`
* Run `Docker Desktop`, this will startup the `docker` environment.
* In a working directory, execute `npm install dodex-quarkus`
* `cd ...\dodex-quarkus\handicap`
* Execute buildimage.bat, the envoy-dev image should be created.
* Execute runenvoy.bat to build and run the container.
* In `...\dodex-quarkus`, execute `set DEFAULT_DB=h2` and `set USE_HANDICAP=true`.
* Execute `.\gradlew quarkusDev`.
* Run in a browser `localhost:8089/handicap.html`.

  __Note:__ The install is run in a __cmd__ terminal not a **power-shell** terminal.

### Handicap File Structure

```
src/grpc/
└── client
    ├── config
    │   ├── esbuild.config.js
    │   └── webpack.config.js
    ├── css
    │   ├── app.css
    │   └── dtsel
    │       └── dtsel.css
    ├── html
    │   └── index.template.html
    ├── js
    │   ├── client.js
    │   ├── country-states
    │   │   ├── index.html
    │   │   └── js
    │   │       └── country-states.js
    │   ├── dodex
    │   │   └── index.js
    │   ├── dtsel
    │   │   ├── dtsel.js
    │   │   ├── LICENSE
    │   │   └── README.md
    │   ├── handicap
    │   │   ├── json
    │   │   │   ├── golfer.json
    │   │   │   ├── rating.json
    │   │   │   └── score.json
    │   │   └── protos
    │   │       ├── handicap_grpc_web_pb.js
    │   │       └── handicap_pb.js
    │   └── validate
    │       └── validate-form.js
    ├── package.json
    ├── package-lock.json
    ├── proto
    ├── protos
    │   └── handicap.proto
    └── static
        ├── content.js
        ├── content.json
        ├── content.private.js
        ├── dodex_g.ico
        ├── favicon.ico
        ├── golf01.jpg
        ├── golf02.jpg
        ├── golf03.jpg
        ├── golf04.jpg
        ├── golf05.jpg
        ├── golf06.jpg
        ├── golf07.jpg
        ├── golf08.jpg
        ├── golf09.jpg
        ├── golf10.jpg
        ├── golf11.jpg
        └── more_horiz.png

src/main/kotlin/
└── golf
    └── handicap
        ├── Course.kt
        ├── Courses.kt
        ├── db
        │   ├── PopulateCourse.kt
        │   ├── PopulateDatabase.kt
        │   ├── PopulateGolfer.kt
        │   ├── PopulateGolferScores.kt
        │   ├── PopulateScore.kt
        │   ├── rx
        │   │   ├── Handicap.kt
        │   │   ├── IPopulateCourse.kt
        │   │   ├── IPopulateGolfer.kt
        │   │   ├── IPopulateGolferScores.kt
        │   │   ├── IPopulateScore.kt
        │   │   ├── PopulateCourse.kt
        │   │   ├── PopulateGolfer.kt
        │   │   ├── PopulateGolferScores.kt
        │   │   ├── PopulateScore.kt
        │   │   └── SqlConstants.kt
        │   └── SqlConstants.kt
        ├── generated
        │   ├── DefaultCatalog.kt
        │   ├── DefaultSchema.kt
        │   ├── keys
        │   │   └── Keys.kt
        │   └── tables
        │       ├── Course.kt
        │       ├── Golfer.kt
        │       ├── Ratings.kt
        │       ├── records
        │       │   ├── CourseRecord.kt
        │       │   ├── GolferRecord.kt
        │       │   ├── RatingsRecord.kt
        │       │   └── ScoresRecord.kt
        │       ├── references
        │       │   └── Tables.kt
        │       └── Scores.kt
        ├── Golfer.kt
        ├── HandicapConstants.kt
        ├── Handicap.kt
        ├── routes
        │   ├── GrpcRoutes.kt
        │   └── HandicapRoutes.kt
        └── Score.kt

src/main/java/dmo/fs/db/handicap/
├── DbConfiguration.java
├── DbDefinitionBase.java
├── DbH2.java
├── DbMariadb.java
├── DbPostgres.java
├── HandicapDatabaseH2.java
├── HandicapDatabase.java
├── HandicapDatabaseMariadb.java
├── HandicapDatabasePostgres.java
├── rx
│   ├── DbConfiguration.java
│   ├── DbDefinitionBase.java
│   ├── DbSqlite3.java
│   └── HandicapDatabaseSqlite3.java
└── utils
    ├── ColorUtilConstants.java
    ├── DodexUtil.java
    ├── JooqGenerate.java
    └── ParseQueryUtilHelper.java

src/main/proto
└── handicap.proto

```