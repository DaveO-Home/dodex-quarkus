#  

## React Integration Testing with Karma, Jasmine and Quarkus

The basic idea is to build a single page(spa) production application ensuring consistent and stable code using JavaScript, CSS and Bootstrap linting and automated unit and integration(e2e) testing.

[Building Front-End Production Bundle](#production-build)

[Building Front-End Test Bundle](#test-build)

[Development Overview](#development)

### Bundle Tools

> 1. [Fusebox](#fusebox)

[Installation](#installation)

### Main Tools

  1. Quarkus
  2. Vertx
  3. Gradle
  4. Gulp
  5. Karma
  6. Jasmine
  7. Mocha/Allure reporting
  8. Any Browser with a karma launcher
  9. Fuse-Box v4, javascript code bundler
  10. Node, Npm

### Installation

[Top](#react-integration-testing-with-karma-jasmine-and-quarkus)

**Install Assumptions:**

  1. OS Linux or Windows(Tested on Windows10)
  2. Node and npm
  3. Gulp4 - use `npx gulp` if not installed globally.
  4. Google Chrome
  5. Firefox
  6. Dodex-Vertx

**Desktop:**

  `cd` to `<dodex-quarkus install>/src/spa-react`.

```bash
  npm install
```

  To install all required dependencies.
  
  __Note__ Npm will produce vulnerability warnings, these are mostly from the testing tools, specifically `jasmine-jquery` which is no longer actively maintained.
  
  The test build will generate the spa application in `src/main/resources/static/dist_test/react-fusebox` and production in `src/main/resources/static/dist/react-fusebox`. The application is accessible after rebuilding the Quarkus test server. When modifying Java code, refreshing the browser window will rebuild the backend application and restart the Quarkus server. However, Quarkus hangs when modifying Javascript and rebuilding the Fuse-Box bundle.
  __Important;__ You must manually stop and restart the Quarkus Sever(`gradlew quarkusDev`) to review the new front-end Javascript code. This is also required when copying front-end static content(`gulp copy`) to the application static directory.

**Client:**

  After building the application(see Production Build and Test Build), view the application with a Browser @

  `localhost:8089/dist/react-fusebox/appl/testapp.html` or `localhost:8089/dist_test/react-fusebox/appl/testapp_dev.html`.

## Production Build

[Top](#react-integration-testing-with-karma-jasmine-and-quarkus)

1. Start the Quarkus test server `cd <install>` and execute `gradlew quarkusDev`.
2. `cd <install>/src/spa-react/devl`. This is the directory used for building the front-end and testing the application(both front-end and back-end).
3. Execute `gulp prod` or `npx gulp prod` if you do not have Gulp installed globally. This should run all tests and if successful will produce the production bundle and static content in `src/main/resources/static/dist/react-fusebox`.
   __Note;__ the javascript linting uses `eslint:recommended` which forces double-quoted strings and semi-colon statement endings etc. This can be changed in `<install>/src/spa-react/devl/.eslintrc.js`.
4. View the application using the production bundle at `localhost:8089/dist/react-fusebox/appl/testapp.html`.
5. You can run `gulp prd` to bypass the testing.
6. Executing `gulp prd -l` will produce a local version in `<install>/src/dist/react-fusebox` for viewing with Node without the Java back-end. Execute `node koa` from the `devl` directory and view in a browser at `localhost:3088/dist/react-fusebox/appl/testapp.html`.

    __Note;__ see dodex-quarkus for building the production jar.

    __Important:__ the test directory `dist_test` is removed from the quarkus server during the production build. Execute `gulp rebuild` or `gulp test` to rebuild the test front-end application.

## Test Build

[Top](#react-integration-testing-with-karma-jasmine-and-quarkus)

The javascript ***Gulp*** task runner is used to accomplish test execution, see `src/spa-react/devl/gulpfile.js`. These tasks will execute the ***Karma*** test runner with the ***Jasmine*** assertion library, see `src/spa-react/devl/karma.conf.js`.  Karma can run both unit and localized tests as well as integration testing across **HTTP**. The default is to run intergration testing, therefore the Dodex-Quarkus server must be running. The integration testing runs best with only one browser, the default is ***Chrome***. Use the `USE_BROWSERS` environment variable to switch browsers or add additional browsers, e.g. `export USE_BROWSERS=FirefoxHeadless,ChromeHeadless,Opera`.

### Test Tasks

Run these commands from `src/spa-react/devl`.

1. `gulp test` ***Currently does not work with Quarkus***
    * default, run once with headless browser
    * builds the ***React*** application using ***Fuse-Box***
    * copies static content
    * location `src/main/resources/static/dist_test` (accessible to Quarkus)
    * executes the unit tests in `src/spa-react/tests`
    * executes the integration tests in `src/spa-react/appl/jasmine`
    * reports the results to the console, see below
    * view in browser @ `http://localhost:8089/dist_test/react-fusebox/appl/testapp_dev.html`
2. `gulp rebuild`
    * rebuild and redeploy the ***React*** application without testing(Must stop and start the Quarkus Server).
3. `gulp acceptance`
    * run tests without rebuilding, very useful when using `gulp watch`(Run after running `gulp rebuild` and restarting Quarkus).
4. `gulp watch` (Currently not useful without manually restarting the Quarkus server)
    * ***Fuse-Box*** watcher to rebuild application on javascript code changes
    * once the verticle is redepoyed, execute `gulp acceptance` to test new/changed code.
    * changes are also viewable in the browser
5. `gulp tdd` Test driven development(requires restarting the Quarkus Server)
    * default, run continuously with normal browser
    * run with `gulp watch` to automatically test while developing
6. `gulp development` (Not working with Quarkus)
    * run both `gulp tdd` and `gulp watch` in the same terminal window
7. `gulp copy`
    * Copy any changed content to the `Quarkus` static directory
    * Manually restart ***Dodex-Quarkus*** to view new static content
8. `gulp lint`
    * Run linting for Javascript, CSS and Bootstrap
9. `Allure Reporting`
    * Run `gulp acceptance -a` or `gulp prod -a` for ***Allure*** reporting
    * Creates results in `devl/allure-results`
    * Execute `npm run allure` to generate reports in `devl/allure-reports`
    * Execute `npm start` to startup Node server to view
    * Use browser to view @ `http://localhost:3088`
10. `gulp hmr`
    * Runs ***Fuse-Box*** hot module reload for the `React` application.
    * Runs a Node http Server and makes front-end javascript changes visible
    * Back-end Java is not accessible
    * Use browser to view @ `http://localhost:3087/dist_test/react-fusebox/appl/testapp_dev.html`
11. `gulp preview`
    * Runs ***Fuse-Box*** to build production front-end without minify and starts a node http server
    * Use browser to view @ `http://localhost:3087/dist/react-fusebox/appl/testapp.html`

Running tests in local mode (no backend java integration tests)

1. The test tasks `test, acceptance, watch, tdd, rebuild, hmr, preview, prod` can all be executed with the `-l` parameter e.g. `gulp test -l`.
2. The builds are located in `src/dist_test` or `src/dist`.
3. When running `gulp hmr -l` view with `http://localhost:3087/dist_test/react-fusebox/appl/testapp_dev.html`

### Java Integration Testing

* The demo application is interfaced with back-end java and database via the `Log In` popup. A simple login table is stored in a local ***Sqlite3*** database, see `src/main/java/dmo/fs/spa...`. Therefore, to view the Dodex and Table View modules, a user must be created and logged in.

* Because testing is via http, all of the tests evaluate `Dodex-Quarkus` delivered content. However, only the `Popup Login Form` tests actually assert complete fullstack processes, i.e. adding, logging-in and logging-out a user.

* The front-end login code is located in `src/spa-react/appl/js/login.js` and the back-end can be tracked from `src/main/java/dmo/fs/spa/router/SpaRoutes.java`.
  
### A test result might look like

```text
START:
Chrome Headless 81.0.4044.129 (Linux x86_64) LOG: 'You should get 45 successful specs.'
LOG: 'Firefox 75: You should get 45 successful specs.'
  Unit Tests - Suite 1
    ✔ Verify that browser supports Promises
    ✔ ES6 Support
  Unit Tests - Suite 2
    ✔ Is Karma active
    ✔ Verify NaN
  Unit Tests - Suite 3
    ✔ Strip Canjs Warning Code
    ✔ Strip Fuse-Box Block Code
  Popper Defined - required for Bootstrap
    ✔ is JQuery defined
    ✔ is Popper defined
  Application Unit test suite - AppTest
    ✔ Is Welcome Page Loaded
    ✔ Is Pdf Loaded
    ✔ Is Tools Table Loaded
    Testing Menulinks Router
      ✔ is table loaded from router component
      ✔ is pdf loaded from router component
    Load new tools page
      ✔ setup and click events executed.
      ✔ did Redux set default value.
      ✔ new page loaded on change.
      ✔ did Redux set new value.
      ✔ verify state management
    Contact Form Validation
      ✔ Contact form - verify required fields
      ✔ Contact form - validate populated fields, email mismatch.
      ✔ Contact form - validate email with valid email address.
      ✔ Contact form - validate form submission.
    Popup Login Form
      ✔ Login form - verify modal with login loaded
      ✔ Login form - verify required fields
      ✔ Login form - verify user name pattern mismatch
      ✔ Login form - verify password pattern mismatch
      ✔ Login form - Make sure test user removed
      ✔ Login form - verify java vertx backend routing
      ✔ Login form - Create New User, click checkbox & fillout form
      ✔ Login form - Create New User, submit & verify new user
      ✔ Login form - Log Out User
      ✔ Login form - Reopen Login
      ✔ Login form - Remove added User
      ✔ Login form - verify cancel and removed from DOM
    Dodex Operation Validation
      ✔ Dodex - loaded and toggle on icon mousedown
      ✔ Dodex - Check that card A is current and flipped on mousedown
      ✔ Dodex - Check that card B is current and flipped on mousedown
      ✔ Dodex - Flip cards A & B back to original positions
      ✔ Dodex - Flip multiple cards on tab mousedown
      ✔ Dodex - Add additional app/personal cards
      ✔ Dodex - Load Login Popup from card1(A)
    Dodex Input Operation Validation
      ✔ Dodex Input - popup on mouse double click
      ✔ Dodex Input - Verify that form elements exist
      ✔ Dodex Input - verify that uploaded file is processed
      ✔ Dodex Input - close popup on button click

Finished in 15.164 secs / 11.543 secs @ 16:58:26 GMT-0700 (Pacific Daylight Time)

SUMMARY:
✔ 90 tests completed
```

## Development

[Top](#react-integration-testing-with-karma-jasmine-and-quarkus)

A test and development scenario for ***Dodex-Quarkus***.

1. Open a desktop terminal window
2. `cd <dodex-quarkus install>`, make sure ***Dodex*** is installed `npm install` in src/main/resources/static
3. `gradlew run`
4. Open a desktop terminal window
5. `cd <dodex-vertx install>/src/spa-react/devl`, make sure ***React*** demo application is installed `npm install` in src/spa-react
6. `gulp watch`
7. When developing ***Java***, refreshing the browser window will redeploy ***Dodex-Quarkus***.
8. To test Java code, Open a desktop terminal window `cd <dodex-quarkus install>` and execute `gradlew test`
9. When developing ***Javascript***, issuing a `save` will rebuild the ***Fuse-Box*** bundle, you will have to manually restart the Quarkus server
10. Open a desktop terminal window
11. `cd <dodex-quarkus install>/src/spa-react/devl`
12. `gulp acceptance` to run tests
13. When changing static content, execute `gulp copy`, and the Quarkus must be manually restarted

### Fusebox

[Top](#react-integration-testing-with-karma-jasmine-and-vertx)

A blazing fast js bundler/loader with a comprehensive API <https://github.com/fuse-box/fuse-box>

* Using Fuse-Box V4 (@next), should be finalized in 2020
* Configuration in `src/spa-react/devl/fuse.js` and `src/spa-react/devl/gulpfile.js` (functions runFusebox and fuseboxConfig)
