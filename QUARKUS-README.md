# dodex-quarkus project

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```bash
./gradlew quarkusDev
```

## Packaging and running the application

The application is packageable using `./gradlew quarkusBuild`.
It produces the executable `dodex-quarkus-2.1.0-runner.jar` file in `build` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/lib` directory.

The application is now runnable using `java -jar build/dodex-quarkus-2.1.0-runner.jar`.

If you want to build an _über-jar_, do option to the command line:

```bash
./gradlew quarkusBuild -Dquarkus.package.type=uber-jar
```

## Creating a native executable

You can create a native executable using: `./gradlew build -Dquarkus.package.type=native`.

Or you can use Docker to build the native executable using: `./gradlew buildNative --docker-build=true`.

You can then execute your binary: `./build/dodex-quarkus-1.0.0-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/gradle-tooling#building-a-native-executable .
