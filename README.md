# Gradle Client

![GitHub License](https://img.shields.io/github/license/eskatos/gradle-client)
![GitHub Top Language](https://img.shields.io/github/languages/top/eskatos/gradle-client)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/eskatos/gradle-client/ci.yml)
![GitHub Issues](https://img.shields.io/github/issues/eskatos/gradle-client)

This is a desktop application acting as a Gradle Tooling API client.

## Usage

You can download the latest version of _Gradle Client_ for macos, Linux or Windows from the [Releases](https://github.com/gradle/gradle-client/releases) assets.

Once installed on your system, simply launch the application.

### Adding a build

The first screen shown by the _Gradle Client_ allows you to add local Gradle builds you want to interact with.

Click the _Add build_ button and pick the folder of a local build.
Note that the folder must contain a `settings.gradle(.kts|.dcl)` file to be accepted.

Each added build is displayed in a list.
From that list you can remove a build by clicking on the cross ╳ button present on the right.

By clicking on a build in the list you navigate to the connection screen.

### Connecting to a build

This screen allows you to define the parameters necessary to connect to a Gradle build:

**Java Home** - _required_

* Defaults to the `JAVA_HOME` environment variable if present.
* Click the folder 📁 button on the right and select a valid Java installation folder from your system to define a specific _Java Home_. 

**Gradle User Home** - _optional_

* Empty by default. When empty the default [_Gradle User Home_](https://docs.gradle.org/current/userguide/directory_layout.html#dir:gradle_user_home) will be used.
* Click the folder 📁 button on the right and select a folder to be used as the _Gradle User Home_ for this build.
* Most of the time you can leave this empty.

**Gradle Distribution** - _required_

* This parameter defines which Gradle version will be used to connect to the build.
* It has three possible values: _Wrapper_, _Specific Version_ or _Local Installation_.
  * _Wrapper_
    * This is the default.
    * The version of Gradle defined by the [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper_basics.html) present in the build will be used.
  * _Specific Version_
    * If you pick _Specific Version_, you will also need to specify which Gradle version to use.
    * The list of known released and nightly Gradle versions is available for you to pick from. 
  * _Local Installation_
    * If you pick _Local Installation_, you will also need to specify the local path to a Gradle installation.
    * Click the folder 📁 button on the right and select a valid folder of a local Gradle installation.

Once you are done defining the connection parameters you can click the _Connect_ button to continue.

### Interacting with a build

Once connected you will see a list of available actions on the left.
Clicking any of these will run the action.
At the bottom of the screen you can expand a drawer to read the logs.

**Build Environment**

* Informs about the build environment, like Gradle version or the Java home in use.
* Fetches the [BuildEnvironment](https://docs.gradle.org/current/javadoc/org/gradle/tooling/model/build/BuildEnvironment.html) Tooling API model.
* This action doesn't require any configuration of the build.

**Build Model**

* Informs about the Gradle build.
* Fetches the [BuildModel](https://docs.gradle.org/current/javadoc/org/gradle/tooling/model/BuildModel.html) Tooling API model.
* This action requires the completion of the [initialization phase](https://docs.gradle.org/current/userguide/build_lifecycle.html#sec:initialization) of the build.
* No subprojects of the build are configured.

**Projects Model**

* Informs about the projects of the Gradle build.
* Fetches the [ProjectModel](https://docs.gradle.org/current/javadoc/org/gradle/tooling/model/ProjectModel.html) Tooling API model.
* This action requires the completion of the [configuration phase](https://docs.gradle.org/current/userguide/build_lifecycle.html#sec:configuration) of the build.
* All subprojects of the build are configured.

**Declarative Schema** (_Only for builds using [Declarative Gradle](https://declarative.gradle.org/)_)

* Informs about the schema of the available _Software Types_.
* Fetches the [DeclarativeSchemaModel](https://github.com/gradle/gradle/blob/10b91d86d67226538bd721a2ee2aefb5233947d5/platforms/core-configuration/declarative-dsl-tooling-models/src/main/java/org/gradle/declarative/dsl/tooling/models/DeclarativeSchemaModel.java#L22) Tooling API model.
* This action requires the completion of the [initialization phase](https://docs.gradle.org/current/userguide/build_lifecycle.html#sec:initialization) of the build.
* No subprojects of the build are configured.

**Declarative Documents** (_Only for builds using [Declarative Gradle](https://declarative.gradle.org/)_)

* Informs about the declarative build definition and allows to trigger mutations.
* Fetches the [DeclarativeSchemaModel](https://github.com/gradle/gradle/blob/10b91d86d67226538bd721a2ee2aefb5233947d5/platforms/core-configuration/declarative-dsl-tooling-models/src/main/java/org/gradle/declarative/dsl/tooling/models/DeclarativeSchemaModel.java#L22) Tooling API model.
* This action requires the completion of the [initialization phase](https://docs.gradle.org/current/userguide/build_lifecycle.html#sec:initialization) of the build.
* No subprojects of the build are configured.
* Parsing, analysis and mutation of the `.gradle.dcl` files is done in the _Gradle Client_.

### Navigating in the application

On each screen you can use the back ⬅ button to navigate to the previous screen.

You can quit the application by closing its window or by hitting `Ctrl-Q` / `Cmd-Q`.

## Building Gradle Client

> ⚠️ These instructions are for software developers working on the _Gradle Client_. Usage instructions can be found above.

The build requires Java 17 with `jlink` and `jpackage` JDK tools.
The build will fail to configure with the wrong Java version.
Building release distributables will fail if the required JDK tools are not available.

```shell
# Run from sources
./gradlew :gradle-client:run

# Run from sources in continuous mode
./gradlew -t :gradle-client:run

# Run debug build type from build installation
./gradlew :gradle-client:runDistributable

# Run release build type from build installation
./gradlew :gradle-client:runReleaseDistributable
```

To add more actions start from [GetModelAction.kt](./gradle-client/src/jvmMain/kotlin/org/gradle/client/ui/connected/actions/GetModelAction.kt).

## Packaging Gradle Client

Packaging native distributions is platform dependent.

The GitHub Actions based CI builds the native distributions.
They are attached as artifacts to each workflow run.
They are also automatically attached as release assets when building a tag.

### Mac

```shell
# Package DMG on MacOS
./gradlew :gradle-client:packageReleaseDmg
```

DMG file is output in [gradle-client/build/compose/binaries/main-release/dmg](./gradle-client/build/compose/binaries/main-release/dmg).

### Linux

```shell
# Package DEB on Linux
./gradlew :gradle-client:packageReleaseDeb
```

DEB file is output in [gradle-client/build/compose/binaries/main-release/deb](./gradle-client/build/compose/binaries/main-release/deb).

### Windows

```shell
# Package MSI on Windows
./gradlew :gradle-client:packageReleaseMsi
```

MSI file is output in [gradle-client/build/compose/binaries/main-release/msi](gradle-client/build/compose/binaries/main-release/msi).
