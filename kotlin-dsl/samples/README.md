Sample Kotlin-based Gradle build scripts
========================================

_See instructions below in order to [set up your dev environment](#set-up-dev-environment) to benefit from [the available IDE support](#explore-available-ide-support)._

The Gradle projects in this directory demonstrate typical use cases with and features available in the Gradle Kotlin DSL. They include:

 - [`ant`](./ant): demonstrates how to use Ant from Gradle via the Ant Groovy Builder
 - [`build-cache`](./build-cache): demonstrates how to configure the Gradle build cache
 - [`build-scan`](./build-scan): demonstrates how to apply and configure the `org.gradle.build-scan` plugin
 - [`buildSrc-plugin`](./buildSrc-plugin): demonstrates how to use the `kotlin-dsl` and `java-gradle-plugin` plugins together in `buildSrc`
 - [`code-quality`](./code-quality): demonstrates how to configure Gradle code quality plugins
 - [`composite-builds`](./composite-builds): demonstrates how to use [Composite Builds](https://docs.gradle.org/current/userguide/composite_builds.html)
 - [`copy`](./copy): demonstrates typed task declarations, and configuration of a Gradle `CopySpec`
 - [`domain-objects`](./domain-objects): demonstrates how to create and configure a `NamedDomainObjectContainer` from a Kotlin build script.
 - [`extra-properties`](./extra-properties): demonstrates the use of `extra` properties (equivalent of the `ext` properties found in Gradle Script Groovy)
 - [`gradle-plugin`](./gradle-plugin): demonstrates a Gradle plugin implemented in Kotlin and taking advantage of the `kotlin-dsl` plugin
 - [`groovy-interop`](./groovy-interop): demonstrates how to interact with Groovy code from Kotlin
 - [`hello-android`](./hello-android): demonstrates a Kotlin-based Gradle build script for a Kotlin-based Android project
 - [`hello-coroutines`](./hello-coroutines): demonstrates how to enable experimental support for [coroutines in Kotlin](https://kotlinlang.org/docs/reference/coroutines.html)
 - [`hello-js`](./hello-js): demonstrates a Kotlin-based Gradle build script for a project that is itself written in Kotlin and targets JavaScript
 - [`hello-kapt`](./hello-kapt): demonstrates a Kotlin-based Gradle build script for a project that is itself written in Kotlin and uses [`kapt`](https://kotlinlang.org/docs/reference/kapt.html) (Kotlin Annotation Processing Tool)
 - [`hello-kotlin`](./hello-kotlin): demonstrates a Kotlin-based Gradle build script for a project that is itself written in Kotlin
 - [`hello-world`](./hello-world): demonstrates plugin application and configuration, dependency management, JUnit testing
 - [`kotlin-friendly-groovy-plugin`](./kotlin-friendly-groovy-plugin): demonstrates a Groovy Gradle plugin and its use from Kotlin-based build scripts
 - [`maven-plugin`](./maven-plugin): demonstrates how to configure the Gradle `maven` plugin
 - [`maven-publish`](./maven-publish): demonstrates how to configure the Gradle `maven-publish` plugin
 - [`model-rules`](./model-rules): demonstrates the use of model rules
 - [`modularity`](./modularity): demonstrates the use of `apply(from = "")` to modularize build scripts
 - [`multi-kotlin-project`](./multi-kotlin-project): demonstrates a [multi-project build](https://docs.gradle.org/current/userguide/multi_project_builds.html) comprising two Kotlin based projects
 - [`multi-kotlin-project-config-injection`](./multi-kotlin-project-config-injection): demonstrates a [multi-project build](https://docs.gradle.org/current/userguide/multi_project_builds.html) comprising two Kotlin based projects in which all `KotlinCompile` tasks belonging to the subprojects are configured by the root project
 - [`multi-kotlin-project-with-buildSrc`](./multi-kotlin-project-with-buildSrc): demonstrates a [multi-project build](https://docs.gradle.org/current/userguide/multi_project_builds.html) comprising two Kotlin based projects with custom build logic in `buildSrc`
 - [`multi-project-with-buildSrc`](./multi-project-with-buildSrc): demonstrates a [multi-project build](https://docs.gradle.org/current/userguide/multi_project_builds.html) with custom build logic in `buildSrc`, including a custom task
 - [`precompiled-script-plugin`](./precompiled-script-plugin): demonstrates a Gradle plugin implemented as a precompiled script 
 - [`project-properties`](./project-properties): demonstrates project property access via [delegated properties](https://kotlinlang.org/docs/reference/delegated-properties.html)
 - [`project-with-buildSrc`](./project-with-buildSrc): demonstrates a single-project build with custom build logic, extension properties and extension functions in `buildSrc`
 - [`provider-properties`](./provider-properties): demonstrates usage of lazily evaluated properties to [map extension properties to task properties](https://docs.gradle.org/4.0-milestone-2/userguide/custom_plugins.html#sec:mapping_extension_properties_to_task_properties)
 - [`source-control`](./source-control): demonstrates how to use external [source dependencies](https://github.com/gradle/gradle-native/issues/42)
 - [`task-dependencies`](./task-dependencies): demonstrates explicit configuration of task dependencies
 - [`testkit`](./testkit): demonstrates how to test a Gradle plugin written in Kotlin using [TestKit](https://docs.gradle.org/current/userguide/test_kit.html)

Set up dev environment
----------------------

_Note: Currently, these instructions only address working with IntelliJ IDEA. In the future, they'll be expanded to include working with Eclipse._

### Install IntelliJ IDEA

Version 2017.3.3 or better is required, and can be downloaded from https://www.jetbrains.com/idea.

### Install IDEA Kotlin Plugin

_Note: the specific version of the Kotlin plugin matters._

All the samples should work against the latest Kotlin plugin, _1.2.20_ at the time of this writing, from the _Stable_ channel accessible via the _Tools_ > _Kotlin_ > _Configure Kotlin Plugin Updates_ action.

Set up a sample project
-----------------------

### Clone the Gradle Kotlin DSL repository

If you have not already done so, clone the kotlin-dsl repository:

    git clone git@github.com:gradle/kotlin-dsl.git # ($REPO_ROOT)

_Note: The remainder of these instructions focus on the `hello-world` sample project, but will work equally well for any of the other samples._

### Import the sample project into IDEA

In IDEA, go to `File->Open...` and navigate to `$REPO_ROOT/samples/hello-world`.

When prompted, choose "Use default Gradle wrapper".

The project should import without errors.

### Explore available IDE support

You're now ready to explore what's possible with the Gradle Kotlin DSL in IDEA. Generally speaking, things should "just work", as they would in the context of any other Java or Kotlin code you would write in IDEA.

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
