# Changelog

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
