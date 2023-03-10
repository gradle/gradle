The Gradle team is excited to announce Gradle @version@.

Time spent in the configuration phase slows down feedback loops.
In Gradle 6.6, Gradle introduced the configuration cache, which speeds up builds by caching and skipping the configuration phase.
This release [promotes the configuration cache feature to stable](#promoted-features) with a [large number of other improvements to it](#configuration-cache-improvements).

The Kotlin DSL has been improved with [many changes](#kotlin-dsl) to help build authors write simpler build scripts and better plugins.
As an experimental feature, Kotlin DSL also has a [simple assignment for Gradle `Property` types](#kotlin-assign).

In [JVM-based projects](#jvm), Java 20 can be used to compile, test and run Java projects and CodeNarc analysis runs in parallel by default.

This release also contains several other [general improvements](#general) and [bug fixes](#fixed-issues).

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:
[André Sousa](https://github.com/beroso),
[Attila Király](https://github.com/akiraly),
[Aurimas](https://github.com/liutikas),
[Björn Kautler](https://github.com/Vampire),
[Bo Zhang](https://github.com/blindpirate),
[Christoph Dreis](https://github.com/dreis2211),
[David Morris](https://github.com/codefish1),
[DJtheRedstoner](https://github.com/DJtheRedstoner),
[Gabriel Feo](https://github.com/gabrielfeo),
[J.T. McQuigg](https://github.com/JT122406),
[JavierSegoviaCordoba](https://github.com/JavierSegoviaCordoba),
[JayaKrishnan Nair K](https://github.com/jknair0),
[Jeff Widman](https://github.com/jeffwidman),
[kackey0-1](https://github.com/kackey0-1),
[Martin Bonnin](https://github.com/martinbonnin),
[Martin Kealey](https://github.com/kurahaupo),
[modmuss50](https://github.com/modmuss50),
[pan93412](https://github.com/pan93412),
[Sebastian Schuberth](https://github.com/sschuberth),
[Simon Marquis](https://github.com/SimonMarquis),
[TheDadda](https://github.com/TheDadda),
[Thrillpool](https://github.com/Thrillpool),
[valery1707](https://github.com/valery1707),
[Xin Wang](https://github.com/scaventz),
[Yanshun Li](https://github.com/Chaoba)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features, performance and usability improvements

### Configuration cache improvements

TODO - Java lambdas are supported, and unsupported captured values are reported.

TODO - File collections queried at configuration time are treated as configuration inputs.

TODO - File system repositories are fully supported including dynamic versions in Maven, Maven local, and Ivy repositories

<a name="kotlin-dsl"></a>
### Kotlin DSL improvements

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an alternative syntax to the Groovy DSL with an enhanced editing experience in supported IDEs — superior content assistance, refactoring, documentation, and more.

<a name="kotlin-assign"></a>
#### Experimental simple property assignment in Kotlin DSL scripts

As an opt-in feature, it is now possible to use the `=` operator to assign values to `Property` types in Kotlin scripts as an alternative to the `set()` method:

```kotlin
interface Extension {
    val description: Property<String>
}

// register "extension" with type Extension
extension {
    // Current: Using the `set()` method call
    description.set("Hello Property")
    // Experimental: lazy property assignment enabled
    description = "Hello Property"
}
```

This reduces the verbosity of Kotlin DSL when [lazy property types](userguide/lazy_configuration.html#lazy_properties) are used to configure tasks and extensions.
It also makes Kotlin DSL behavior consistent with Groovy DSL behavior, where using `=` to assign lazy properties has always been available.

Lazy property assignment for Kotlin scripts is an experimental opt-in feature.
For more information, see [Kotlin DSL Primer](userguide/kotlin_dsl.html#kotdsl:assignment).

#### Access to version catalog for plugins in the `plugins {}` block

Version catalog accessors for plugin aliases in the `plugins {}` block aren't shown as errors in IntelliJ IDEA and Android Studio Kotlin script editor anymore.

```kotlin
plugins {
    alias(libs.plugins.jmh)
}
```

If you were using a workaround for this before, see the [corresponding section](userguide/upgrading_version_8.html#kotlin_dsl_plugins_catalogs_workaround) in the upgrading guide.

#### Useful deprecation warnings and errors from Kotlin script compilation

Gradle [Kotlin DSL scripts](userguide/kotlin_dsl.html#sec:scripts) are compiled by Gradle during the configuration phase of your build.

Deprecation warnings found by the Kotlin compiler are now reported on the console when compiling build scripts.
This makes it easier to spot usages of deprecated members in your build scripts.

```text
> Configure project :
w: build.gradle.kts:4:5: 'getter for uploadTaskName: String!' is deprecated. Deprecated in Java
```

Moreover, Kotlin DSL script compilation errors are now always reported in the order they appear in the file.
This makes it easier to figure out the first root cause of a script compilation failure.

```text
* Where:
Build file 'build.gradle.kts' line: 5

* What went wrong:
Script compilation errors:

  Line 5: functionDoesNotExist()
          ^ Unresolved reference: functionDoesNotExist
          
  Line 8: doesNotExistEither = 23
          ^ Unresolved reference: doesNotExistEither
          
2 errors
```

#### Easier access to extensions on the `Gradle` object

The `Gradle` type now declares that it is `ExtensionAware`.
This allows access to extensions without casting to `ExtensionAware` from Kotlin.
This can be useful in [initialization scripts](userguide/init_scripts.html).

```kotlin
// Assuming the Gradle object has an extension of type MyExtension
configure<MyExtension> {
    someProperty.set("value")
}

// Assuming the Gradle object has an optional extra property named 'myOption'
val myOption: String? by extra
```

See the [ExtensionAware](dsl/org.gradle.api.plugins.ExtensionAware.html#org.gradle.api.plugins.ExtensionAware) type documentation for more information.

#### Easier configuration of `Test.forkEvery` from Kotlin

It is now easier to configure the `forkEvery` property of `Test` tasks from Kotlin to set the maximum number of test classes to execute in a forked test process.
The property nullability is now coherent and instead of requiring to use its `setForkEvery(<number>)` setter you can now simply assign it a value:

```kotlin
tasks.test {
    forkEvery = 8
}
```

See the [Test.forkEvery](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:forkEvery) property documentation for more information.

### Kotlin Gradle plugin development improvements

The [`kotlin-dsl` plugin](userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin) provides a convenient way to develop Kotlin-based plugins that contribute build logic.

In addition to plugins written as standalone projects, Gradle also allows you to provide build logic written in Kotlin as [precompiled script plugins](userguide/custom_plugins.html#sec:precompiled_plugins).
You write these as `*.gradle.kts` files in `src/main/kotlin` directory.

#### Easier customization of Kotlin options

Thanks to the Kotlin Gradle Plugin using Gradle lazy properties, the `kotlin-dsl` plugin does not use `afterEvaluate {}` for configuring Kotlin compiler options anymore.
This allows for easier customization of Kotlin options in your build logic without requiring `afterEvaluate {}`.

```kotlin
plugins {
    `kotlin-dsl`
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}
```

The standalone script compilation is also configured to skip the pre-release check to allow referencing Kotlin code compiled with more recent Kotlin language versions on a best-effort basis.

#### `kotlin-dsl` published with proper licensing information

The `kotlin-dsl` plugin is now published to the Gradle Plugin Portal with proper licensing information in its metadata.
The plugin is published under the same license as the Gradle Build Tool: the Apache License Version 2.0.

This makes using the `kotlin-dsl` plugin easier in an enterprise setting where published licensing information is required.

#### Respect `--offline` when building precompiled script plugins

Building precompiled script plugins now respects the [--offline](userguide/command_line_interface.html#sec:command_line_execution_options) command line execution option.

This makes using Gradle plugins that react to `--offline` from precompiled script plugins easier.

#### Less verbose compilation with precompiled script plugins

Building precompiled script plugins includes applying plugins to synthetic projects.
This can produce some console output.

The output is now captured and only shown in case of failures.
By default, this is now less verbose and does not clutter the console output.

The output is captured and only shown in case of failures.

#### Better validation of name and path of precompiled script plugins

Precompiled script plugins must respect the documented [naming conventions](userguide/kotlin_dsl.html#script_file_names).
Gradle will now fail with an explicit and helpful error message when naming conventions are not followed.
For example:

```text
Precompiled script 'src/main/kotlin/settings.gradle.kts' file name is invalid, please rename it to '<plugin-id>.settings.gradle.kts'.
```

Moreover, `.gradle.kts` files present in resources `src/main/resources` are not considered as precompiled script plugins anymore.
This makes it easier to ship Gradle Kotlin DSL scripts in plugins resources.

<a name="jvm"></a>
### JVM

#### Support for building projects with Java 20

Gradle now supports using Java 20 for compiling, testing, and starting other Java programs.
This can be accomplished by configuring your build or task to use a Java 20 [toolchain](userguide/toolchains.html).

Running Gradle itself on Java 20 is not yet supported.

#### Faster Codenarc analysis with parallel execution by default

The Codenarc plugin performs quality checks on your project’s Groovy source files using a static code analyzer.
It now uses the Gradle worker API and JVM toolchains.

CodeNarc now performs analysis via an external worker process which allows it to run in parallel within a single project.
In Groovy projects, this tool will use the same version of Java the project requires.
In other types of projects, it will use the same version of Java used by the Gradle daemon.

For more details, see the [user manual](userguide/codenarc_plugin.html).

<a name="general"></a>
### General Improvements

#### Gradle Wrapper introduces labels for selecting the version

The [`--gradle-version`](userguide/gradle_wrapper.html#sec:adding_wrapper) parameter for the wrapper task now supports using predefined labels to select a version.

The recognized labels are:

- `latest` selects the latest stable version
- `release-candidate` selects the latest release candidate version
- `nightly` selects the latest unstable nightly version
- `release-nightly` selects the latest unstable nightly version for the next release 

More details can be found in the [Gradle Wrapper](userguide/gradle_wrapper.html#sec:adding_wrapper) section.

#### Build Init plugin incubating flag enables more incubating options

When generating a new project with the `init` task with the `--incubating` option, [parallel project execution](userguide/multi_project_configuration_and_execution.html#sec:parallel_execution) and [task output caching](userguide/build_cache.html) will be enabled for the generated project by creating a `gradle.properties` file and setting the appropriate flags in it.

#### Better physical memory management

Gradle attempts to manage its physical memory usage by proactively stopping unused worker processes before starting new ones.
It does this by first checking if the available physical memory can accommodate the heap requirements of a new worker process.
If not, Gradle then looks for unused worker processes that can be stopped to free up enough physical memory for the new process.

Previously, it sought to acquire enough memory to satisfy the minimum heap requirements of the new process.
However, in cases where the minimum heap and maximum heap of the worker process are very different, the memory freed up before the process starts may not be close to sufficient for the eventual size of the process.

Gradle now attempts to acquire enough memory to satisfy the new process's _maximum_ heap requirements.
This causes the physical memory management to be more aggressive when starting up new processes, and in many cases, will result in better overall memory usage.

See [the userguide](userguide/build_environment.html#sec:configuring_jvm_memory) for more information on configuring JVM memory options.

#### Easier consumption of Shared Build Services

There is a [new `@ServiceReference` annotation](userguide/build_services.html#sec:service_references) that makes it easier to consume shared build services.

By annotating a property with `@ServiceReference`,
you no longer need to remember to explicitly declare that your task uses a shared build service via `Task#usesService()`.

If you also provide the name of the service in the annotation, you no longer need to obtain and assign a build service reference to the property explicitly; if a service registration with the given name exists, the corresponding reference is automatically assigned to the property.

More details in the Shared Build Services documentation on [using build services](userguide/build_services.html#sec:using_a_build_service_from_a_task).

### IDE Integration

#### Builds launched via the IDE use the same log level as the command-line

Previously, wehen executing a build via the IDE, the log level settings provided in the project's `gradle.properties` file were ignored.
Some IDE vendors worked around this shortcoming by setting the log level in other ways to meet user expectations.

The Tooling API now honors the `org.gradle.logging.loglevel` setting in the project's `gradle.properties` and applies it as expected to builds started from the IDE.

Learn more about [changing log levels](userguide/logging.html#sec:choosing_a_log_level) in the user manual.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.

See the user manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Configuration cache

The Configuration cache feature is now out of incubation and ready for general consumption.
When enabled, the configuration cache significantly improves build performance by caching the result of the configuration phase and reusing it for subsequent builds.
Configuration cache was introduced as an experimental feature back in [Gradle 6.6](https://docs.gradle.org/6.6/release-notes.html#configuration-caching).

To learn how to benefit from this feature, refer to the [Configuration Cache](userguide/configuration_cache.html) documentation.

### Promoted features in the Provider API

The `ValueSource` API is no longer incubating. The following classes and methods are now considered stable:

* [`ProviderFactory.of(Class, Action)`](javadoc/org/gradle/api/provider/ProviderFactory.html#of-java.lang.Class-org.gradle.api.Action-)
* [`ValueSource`](javadoc/org/gradle/api/provider/ValueSource.html)
* [`ValueSourceParameters`](javadoc/org/gradle/api/provider/ValueSourceParameters.html)
* [`ValueSourceParameters.None`](javadoc/org/gradle/api/provider/ValueSourceParameters.None.html)
* [`ValueSourceSpec`](javadoc/org/gradle/api/provider/ValueSourceSpec.html)

## Fixed issues

<!--
This section will be populated automatically
-->

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure that you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
