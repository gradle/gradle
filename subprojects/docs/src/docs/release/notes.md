The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:
[Attila Király](https://github.com/akiraly),
[Björn Kautler](https://github.com/Vampire),
[DJtheRedstoner](https://github.com/DJtheRedstoner),
[Gabriel Feo](https://github.com/gabrielfeo)
[JayaKrishnan Nair K](https://github.com/jknair0),
[kackey0-1](https://github.com/kackey0-1),
[Martin Bonnin](https://github.com/martinbonnin),
[Martin Kealey](https://github.com/kurahaupo),
[modmuss50](https://github.com/modmuss50),
[Sebastian Schuberth](https://github.com/sschuberth),
[valery1707](https://github.com/valery1707),
[Xin Wang](https://github.com/scaventz),
[Yanshun Li](https://github.com/Chaoba),
[Thrillpool](https://github.com/Thrillpool)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features, performance and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

### Gradle Wrapper

#### Introduced labels for selecting the version

The [`--gradle-version`](userguide/gradle_wrapper.html#sec:adding_wrapper) parameter for the wrapper plugin
now supports using predefined labels to select a version.

The allowed labels are:

- `latest`
- `release-candidate`
- `nightly`
- `release-nightly`

More details can be found in the [Gradle Wrapper](userguide/gradle_wrapper.html#sec:adding_wrapper) section.

<!--

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

### Configuration cache improvements

TODO - Java lambdas are supported, and unsupported captured values are reported.
TODO - File collections queried at configuration time are treated as configuration inputs.
TODO - File system repositories are fully supported including dynamic versions in Maven, Maven local, and Ivy repositories

### Kotlin DSL improvements

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an alternative syntax to the Groovy DSL with an enhanced editing experience in supported IDEs — superior content assistance, refactoring, documentation, and more.

#### Version catalogs for plugins in the `plugins {}` block

Version catalog accessors for plugin aliases in the `plugins {}` block aren't shown as errors in IntelliJ IDEA and Android Studio Kotlin script editor anymore.

```kotlin
plugins {
    alias(libs.plugins.jmh)
}
```

If you were using a workaround for this before, see the [corresponding section](userguide/upgrading_version_8.html#kotlin_dsl_plugins_catalogs_workaround) in the upgrading guide.

#### Kotlin script compilation improvements

Gradle [Kotlin DSL scripts](userguide/kotlin_dsl.html#sec:scripts) are compiled by Gradle during the configuration phase of your build.

Deprecation warnings found by the Kotlin compiler are now reported on the console.
This makes it easier to spot usages of deprecated members in your build scripts.

```text
> Configure project :
w: build.gradle.kts:4:5: 'getter for uploadTaskName: String!' is deprecated. Deprecated in Java
```

Moreover, Kotlin DSL script compilation errors are now always reported in the file order.
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

#### `kotlin-dsl` plugin improvements

The [Kotlin DSL Plugin](userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin) provides a convenient way to develop Kotlin-based projects that contribute build logic.

##### Easier customization of Kotlin options

Thanks to the Kotlin Gradle Plugin now using Gradle lazy properties, the `kotlin-dsl` plugin does not use `afterEvaluate {}` for configuring Kotlin compiler options anymore.
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

##### Published with proper licensing information

The `kotlin-dsl` plugin is now published to the Gradle Plugin Portal with proper licensing information in its metadata.
The plugin is published under the same license as the Gradle Build Tool: the Apache License  Version 2.0.

This makes using the `kotlin-dsl` plugin easier in an enterprise setting where published licensing information is required. 

#### Precompiled Kotlin script plugin improvements

In addition to plugins written as standalone projects, Gradle also allows you to provide build logic written in Kotlin as [precompiled script plugins](userguide/custom_plugins.html#sec:precompiled_plugins).
You write these as `*.gradle.kts` files in `src/main/kotlin` directory.

##### Respect `--offline`

Building precompiled script plugins now respects the [--offline](userguide/command_line_interface.html#sec:command_line_execution_options) command line execution option.

This makes using Gradle plugins that react to `--offline` from precompiled script plugins easier.

##### Less verbose compilation

Building precompiled script plugins includes applying plugins to synthetic projects.
This can produce some console output.

The output is now captured and only shown in case of failures.
By default, this is now less verbose and does not clutter the console output.

The output is captured and only shown in case of failures.

##### Better validation of name and path

Precompiled script plugins must respect documented [naming conventions](userguide/kotlin_dsl.html#script_file_names).
Gradle will now fail with an explicit and helpful error message when naming conventions are not followed.
For example:

```text
Precompiled script 'src/main/kotlin/settings.gradle.kts' file name is invalid, please rename it to '<plugin-id>.settings.gradle.kts'.
```

Moreover, `.gradle.kts` files present in resources `src/main/resources` are not considered as precompiled script plugins anymore.
This makes it easier to ship Gradle Kotlin DSL scripts in plugins resources.

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

### Build Init plugin incubating option changes

When using the `init` task with the `--incubating` option, [parallel project execution](userguide/multi_project_configuration_and_execution.html#sec:parallel_execution) and [task output caching](userguide/build_cache.html) will be enabled for the generated project (by creating a `gradle.properties` file and setting the appropriate flags in it).

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Tooling API improvements

#### Build launched via TAPI applies log level settings of target build set in gradle.properties

  When executing a build via the Tooling API (typically from within an IDE such as IntelliJ), the log level settings provided in the project's `gradle.properties` file have been ignored till now.
  The IDE vendors had to workaround this short coming by setting the log level in other ways to meet user expectations 
  (e.g. with parsing `gradle.properties` and applying corresponding command line options to the build execution.
  
  The improved Tooling API now reads the `org.gradle.logging.loglevel` setting in the project's `gradle.properties` and applies it as expected to the build execution.
  
  Learn more about the [Choosing a log level](userguide/logging.html#sec:choosing_a_log_level) in Gradle.  

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

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
