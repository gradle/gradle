Sample Kotlin-based Gradle build scripts
========================================

_See instructions below in order to [set up your dev environment](#set-up-dev-environment) to benefit from [the available IDE support](#explore-available-ide-support)._

The Gradle projects in this directory demonstrate typical use cases with and features available in Gradle Script Kotlin. They include:

 - [`hello-world`](./hello-world): demonstrates plugin application and configuration, dependency management, JUnit testing
 - [`copy`](./copy): demonstrates typed task declarations, and configuration of a Gradle `CopySpec`
 - [`task-dependencies`](./task-dependencies): demonstrates explicit configuration of task dependencies
 - [`extra-properties`](./extra-properties): demonstrates the use of `extra` properties (equivalent of the `ext` properties found in Gradle Script Groovy)
 - [`project-properties`](./project-properties): demonstrates project property access via [delegated properties](https://kotlinlang.org/docs/reference/delegated-properties.html)
 - [`modularity`](./modularity): demonstrates the use of `applyFrom` to modularize build scripts
 - [`hello-kotlin`](./hello-kotlin): demonstrates a Kotlin-based Gradle build script for a project that is itself written in Kotlin
 - [`hello-android`](./hello-android): demonstrates a Kotlin-based Gradle build script for a Kotlin-based Android project
 - [`multi-kotlin-project`](./multi-kotlin-project): demonstrates a [multi-project build](https://docs.gradle.org/current/userguide/multi_project_builds.html) comprising two Kotlin based projects
 - [`multi-kotlin-project-config-injection`](./multi-kotlin-project-config-injection): demonstrates a [multi-project build](https://docs.gradle.org/current/userguide/multi_project_builds.html) comprising two Kotlin based projects in which all `KotlinCompile` tasks belonging to the subprojects are configured by the root project


Set up dev environment
----------------------

_Note: Currently, these instructions only address working with IntelliJ IDEA. In the future, they'll be expanded to include working with Eclipse._

### Install IntelliJ IDEA

Version 2016.1.2 or better is required, and can be downloaded from https://www.jetbrains.com/idea.

### Install IDEA Kotlin Plugin

_Note: the specific version of the Kotlin plugin matters. If you intend to use the plugin with the official Gradle 3.0 release stick to the Early Access Preview 1.1 channel version otherwise follow the instructions below regardless the version you already have installed._

All the samples should work against the latest Kotlin plugin from the _Early Access Preview 1.1_ channel accessible via the _Configure Kotlin Plugin Updates_ action in IDEA but for the latest and greatest support please install the development version by following these instructions:

 1. Download the plugin from https://teamcity.jetbrains.com/guestAuth/repository/download/bt345/837716:id/kotlin-plugin-1.1.0-dev-2222.zip (_Note: this version will not work with the official Gradle 3.0 release, stick to the official EAP 1.1 from JetBrains if you intend to use Gradle 3.0_)
 2. In IDEA, Go to `Preferences->Plugins->Install from disk`
 3. Install the plugin and restart IDEA


Set up a sample project
-----------------------

### Clone the Gradle Script Kotlin repository

If you have not already done so, clone the gradle-script-kotlin repository:

    git clone git@github.com:gradle/gradle-script-kotlin.git # ($REPO_ROOT)

_Note: The remainder of these instructions focus on the `hello-world` sample project, but will work equally well for any of the other samples._

### Import the sample project into IDEA

In IDEA, go to `File->Open...` and navigate to `$REPO_ROOT/samples/hello-world`.

When prompted, choose "Use default Gradle wrapper".

The project should import without errors.

### Explore available IDE support

You're now ready to explore what's possible with Gradle Script Kotlin in IDEA. Generally speaking, things should "just work", as they would in the context of any other Java or Kotlin code you would write in IDEA.

Start by opening `build.gradle.kts`.

_Note: The very first time you do, IDEA might fail to recognise the
classpath of the script, if that happens, simply restart IDEA. This is
a known issue that will be fixed soon._

Continue with any or all of the following:

#### Syntax highlighting

You should notice that normal Kotlin syntax highlighting works throughout the file.

#### Quick documentation

Try clicking on any type or function in the script, and hit `F1` (or possibly `CTRL-J`, dependending on which IDEA key mapping you use). Notice how you're presented with a quick documentation pop-up complete with that element's Javadoc / KDoc.

#### Navigation to source

Again, try clicking on any type or function in the script and hit `CMD-B`. Notice that you're taken directly to the source for that element.

#### Auto-completion / content assist

Try using `CTRL-SPACE` at various sites in the script, and notice that you're provided with complete content assist regarding what's available at that site.

#### Refactoring

Most any refactoring action that is possible in a Kotlin file should also work in a Kotlin-based Gradle build script. Explore!


See also
--------

See the latest [release notes](../../../releases) for further details on current features and limitations.
