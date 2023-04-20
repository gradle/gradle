The Gradle team is excited to announce Gradle @version@.

This is the first patch release for Gradle 8.1.

It fixes the following issues:
* [#24748](https://github.com/gradle/gradle/issues/24748) MethodTooLargeException when instrumenting a class with thousand of lambdas for configuration cache
* [#24754](https://github.com/gradle/gradle/issues/24754) Kotlin DSL precompiled script plugins built with Gradle 8.1 cannot be used with other versions of Gradle
* [#24788](https://github.com/gradle/gradle/issues/24788) Gradle 8.1 configure freeCompilerArgs for Kotlin in buildSrc breaks build with unhelpful errors

We recommend users upgrade to @version@ instead of 8.1.

---

You'll be happy to know that the configuration cache is now stable and ready for general use.
The configuration cache was introduced in Gradle 6.6 to help speed up builds by caching the result of the [configuration phase](userguide/build_lifecycle.html) and reusing it for subsequent builds.
Plus, there are [several other improvements](#configuration-cache-improvements) that enhance its usability and compatibility that pave the way for further  performance boosts.

The Kotlin DSL has undergone [significant improvements](#kotlin-dsl) to make it easier for build authors to create simpler build scripts and better plugins.
As an experimental feature, Kotlin DSL also has a [simple assignment for Gradle `Property` types](#kotlin-assign).

In [JVM-based projects](#jvm), it is now possible to use Java 20 for compiling, testing, and running Java projects.
Additionally, CodeNarc analysis runs in parallel by default, allowing for faster code quality analysis.

This release also contains several [other improvements](#other) and [bug fixes](#fixed-issues).

See the [What's new in Gradle 8.0](https://gradle.org/whats-new/gradle-8/) for a discussion of changes from 7.0 to 8.0. 

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

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing it for subsequent builds.
This feature, [now promoted  to stable status](#promoted-features), can significantly improve build performance.

#### Securing the configuration cache

To mitigate the risk of accidental exposure of sensitive data, Gradle now encrypts the configuration cache.
Gradle will transparently generate a machine-specific secret key as required, cache it under the [_Gradle user home directory_](userguide/directory_layout.html#dir:gradle_user_home) and use it to encrypt the data in the project specific caches.

By default, the Gradle configuration cache is located under `.gradle/configuration-cache` in your project directory.

Every cache entry contains information about the set of tasks to run, along with their configuration and dependency information.

However, based on a task's implementation or the way it is configured by a plugin or build script, its configuration might contain sensitive information.
It is crucial to keep this information secure.

To enhance security further, make sure to:

* prevent the `.gradle/configuration-cache` folder from being committed to source control or exposed in CI environments;
* secure the _Gradle user home directory_.

#### Dependency verification support

The configuration cache now fully supports [dependency verification](userguide/dependency_verification.html).
Changes to associated files (keyring files or `verification-metadata.xml`) are correctly detected and invalidate the configuration cache if necessary.

#### File system-based repositories support

The configuration cache now fully supports [file-system-based Ivy and Maven repositories](userguide/declaring_repositories.html#sec:supported_transport_protocols).
In particular, this release adds support for dynamic dependencies.
Suppose a dynamic dependency is declared in the build script. 
In that case, changes to the dependency in the file-system-based repository now invalidate the configuration cache to pick up an updated version.

#### Expanded compatibility with core plugins

The [Ivy publishing plugin](userguide/publishing_ivy.html) and the [Signing plugin](userguide/signing_plugin.html) are now compatible with the configuration cache.

The current status of the configuration cache support for all core Gradle plugins can be found in the [configuration cache documentation](userguide/configuration_cache.html#config_cache:plugins).

#### Support of Java lambdas

Gradle can now restore user-provided lambdas from the configuration cache.
Using anonymous classes to implement Single Abstract Method (SAM) interfaces is no longer necessary.
This also applies to Kotlin code: there is no more need to configure the Kotlin compiler to generate classes instead of lambdas during SAM conversion.

This release also improves error reporting for lambdas that capture [unsupported types](userguide/configuration_cache.html#config_cache:requirements:disallowed_types), like `Configuration`.

#### Better error reporting for Groovy closures

This release improves error reporting of unsupported method calls in Groovy closures.
For example, a `doFirst`/`doLast` action uses a method or property of the `Project`, which is unsupported with the configuration cache:

```groovy
tasks.register('echo') {
    doLast { println buildDir }
}
```

Previously, a confusing message of `Could not get unknown property 'buildDir' for task ':echo'` was displayed, but now the error is more accurate:

```text
* Where:
Build file 'build.gradle' line: 2


* What went wrong:
Execution failed for task ':echo'.
> Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.
```

#### Configuration inputs detection improvements

The configuration cache needs to detect when the build logic accesses the "outside world" at configuration time (for example, reading files or environment variables) to invalidate the cache if accessed information changes.
Every recent Gradle release added new detection capabilities, and this release is no exception.
Gradle now detects:

* `FileCollection`s queried at configuration time.
* Methods of `java.io.File` class used to check file existence and read directory contents.
* Methods of `java.nio.files.File` class used to open files for reading and to check file existence.
* Kotlin and Groovy helper methods used to read file contents.

<a name="kotlin-dsl"></a>
### Kotlin DSL improvements

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an alternative syntax to the Groovy DSL with an enhanced editing experience in supported IDEs — superior content assistance, refactoring documentation, and more.

<a name="kotlin-assign"></a>
#### Experimental simple property assignment in Kotlin DSL scripts

As an opt-in feature, it is now possible to use the `=` operator to assign values to `Property` types in Kotlin scripts as an alternative to the `set()` method:

```kotlin
interface Extension {
    val description: Property<String>
}

// register "extension" with type Extension
extension {
    // Current: Using the set() method call
    description.set("Hello Property")
    // Experimental: lazy property assignment enabled
    description = "Hello Property"
}
```

This reduces the verbosity of Kotlin DSL when [lazy property types](userguide/lazy_configuration.html#lazy_properties) are used to configure tasks and extensions.
It also makes Kotlin DSL behavior consistent with Groovy DSL behavior, where using `=` to assign lazy properties has always been available.

Lazy property assignment for Kotlin scripts is an experimental opt-in feature.
It is enabled by adding `systemProp.org.gradle.unsafe.kotlin.assignment=true` to the `gradle.properties` file.

There are three known issues with the IDE integration: [KT-56941](https://youtrack.jetbrains.com/issue/KT-56941), [KT-56221](https://youtrack.jetbrains.com/issue/KT-56221) and [KTIJ-24390](https://youtrack.jetbrains.com/issue/KTIJ-24390).

For more information and current limitations, see the [Kotlin DSL Primer](userguide/kotlin_dsl.html#kotdsl:assignment).

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
Instead of requiring to use its `setForkEvery(<number>)` setter you can now simply assign it a value:

```kotlin
tasks.test {
    forkEvery = 8
}
```

See the [Test.forkEvery](dsl/org.gradle.api.tasks.testing.Test.html#org.gradle.api.tasks.testing.Test:forkEvery) property documentation for more information.

### Improvements to development of Gradle plugins in Kotlin

The [`kotlin-dsl` plugin](userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin) provides a convenient way to develop Kotlin-based plugins that contribute build logic.

In addition to plugins written as standalone projects, Gradle also allows you to provide build logic written in Kotlin as [precompiled script plugins](userguide/custom_plugins.html#sec:precompiled_plugins).
You write these as `*.gradle.kts` files in `src/main/kotlin` directory.

#### Easier customization of Kotlin options

Customization of Kotlin options in your build logic is now easier and no longer  requires `afterEvaluate {}`.

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

The standalone script compilation for build scripts, init scripts, and applied scripts are configured to skip the pre-release check to allow referencing Kotlin code compiled with more recent Kotlin language versions on a best-effort basis.

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

#### Better validation of name and path of precompiled script plugins

Precompiled script plugins must respect the documented [naming conventions](userguide/kotlin_dsl.html#script_file_names).
Gradle will now fail with an explicit and helpful error message when naming conventions are not followed.
For example:

```text
Precompiled script 'src/main/kotlin/settings.gradle.kts' file name is invalid, please rename it to '<plugin-id>.settings.gradle.kts'.
```

Moreover, `.gradle.kts` files present in resources `src/main/resources` are not considered as precompiled script plugins anymore.
This makes it easier to ship Gradle Kotlin DSL scripts in plugins resources.

### JVM

#### Support for building projects with Java 20

Gradle now supports using Java 20 for compiling, testing, and starting other Java programs.
This can be accomplished by configuring your build or task to use a Java 20 [toolchain](userguide/toolchains.html).

You cannot currently run Gradle on Java 20 because Kotlin lacks support for JDK 20.
However, you can expect support for running Gradle with Java 20 in a future version.

#### Faster Codenarc analysis with parallel execution by default

The [Codenarc plugin](userguide/codenarc_plugin.html) performs quality checks on your project’s Groovy source files using a static code analyzer.
It now uses the Gradle worker API and JVM toolchains.

CodeNarc now performs analysis via an external worker process which allows it to run in parallel within a single project.

In Groovy projects, this tool will use the same version of Java the project requires.
In other types of projects, it will use the same version of Java used by the Gradle daemon.

For more details, see the [user manual](userguide/codenarc_plugin.html).

### Plugin publishing to the Gradle Plugin Portal

Version [1.2.0](https://plugins.gradle.org/plugin/com.gradle.plugin-publish/1.2.0) of the `com.gradle.plugin-publish` plugin is now available.
It is required to benefit from the features and bugfixes listed below.

See [documentation](userguide/publishing_gradle_plugins.html) for more details.

#### Dry run for publishing plugins

A new option is added to `publishPlugins` task.
You can validate your plugins before actually publishing them using the `--validate-only` flag:

```sh
$ ./gradlew publishPlugins --validate-only
```

#### Sigstore signing support

With a [plugin](https://plugins.gradle.org/plugin/dev.sigstore.sign) for doing sigstore signing, the portal accepts `*.sigstore` bundle files as an alternate signing solution.

#### Shadow plugin integration fixes

The bug in integration with Shadow plugin that caused JAR manifest to contain Gradle API jars has been fixed.

<a name="other"></a>
### Other Improvements

#### Gradle Wrapper introduces labels for selecting the version

The [`--gradle-version`](userguide/gradle_wrapper.html#sec:adding_wrapper) parameter for the wrapper task now supports using predefined labels to select a version.

The recognized labels are:

- `latest` selects the latest stable version
- `release-candidate` selects the latest release candidate version
- `nightly` selects the latest unstable nightly version
- `release-nightly` selects the latest unstable nightly version for the next release

More details can be found in the [Gradle Wrapper](userguide/gradle_wrapper.html#sec:adding_wrapper) section.

#### Build Init plugin incubating flag enables more incubating features

When generating a new project with the `init` task with the `--incubating` option, [parallel project execution](userguide/multi_project_configuration_and_execution.html#sec:parallel_execution) and [task output caching](userguide/build_cache.html) will be enabled for the generated project by creating a `gradle.properties` file and setting the appropriate flags in it.

#### Better memory management

To better manage memory usage, Gradle proactively stops unused worker processes before starting new ones.

Gradle first checks if the available physical memory can accommodate the maximum heap requirements of a new worker process.
If not, it searches for unused worker processes that can be stopped to free up enough physical memory for the new process.

Previously, Gradle sought to acquire enough memory to satisfy the minimum heap requirements of the new process.
However, in cases where the minimum heap and maximum heap of the worker process are very different, the memory freed up before the process starts may not be close to sufficient for the eventual size of the process.

Gradle now attempts to acquire enough memory to satisfy the new process's _maximum_ heap requirements.

This causes the physical memory management to be more aggressive when starting up new processes, and in many cases, will result in better overall memory usage.

See [the userguide](userguide/build_environment.html#sec:configuring_jvm_memory) for more information on configuring JVM memory options.

#### Easier consumption of Shared Build Services

There is a [new `@ServiceReference` annotation](userguide/build_services.html#sec:service_references) that makes it easier to consume shared build services.

By annotating a property with `@ServiceReference`, you no longer need to remember to explicitly declare that your task uses a shared build service via `Task#usesService()`.

If you also provide the name of the service in the annotation, you no longer need to obtain and assign a build service reference to the property explicitly;
if a service registration with the given name exists, the corresponding reference is automatically assigned to the property.

More details in the Shared Build Services documentation on [using build services](userguide/build_services.html#sec:using_a_build_service_from_a_task).

#### New Dataflow Actions replace `buildFinished` listeners

Previously, Gradle had only `Gradle.buildFinished` listeners to handle the result of the build.
For many reasons, this API doesn't work well with the configuration cache, but there were no proper replacements.
With the new [Dataflow Actions](userguide/dataflow_actions.html) you can now schedule pieces of work to process the result of the build in a way that is configuration-cache compatible.
For example, you can add code to play a sound when the build completes successfully:

```java
class FFPlay implements FlowAction<FFPlay.Parameters> {
    interface Parameters extends FlowParameters {
        @Input
        Property<File> getMediaFile();
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public void execute(Parameters parameters) {
        getExecOperations().exec(spec -> {
            spec.commandLine(
                "ffplay", "-nodisp", "-autoexit", "-hide_banner", "-loglevel", "quiet",
                parameters.getMediaFile().get().getAbsolutePath()
            );
            spec.setIgnoreExitValue(true);
        });
    }
}
```

```java
flowScope.always(FFPlay.class, spec ->
      spec.getParameters().getMediaFile().fileProvider(
          flowProviders.getBuildWorkResult().map(result ->
              new File(
                  soundsDir,
                  result.getFailure().isPresent() ? "sad-trombone.mp3" : "tada.mp3"
              )
          )
      )
  );
```

Unlike callbacks, actions provide the necessary level of isolation to ensure safe configuration caching.

In this release, dataflow actions only provide a replacement for the deprecated `Gradle.buildFinished` callback, but more options to add work outside the task graph are planned.

#### Gradle user home caches are ignored by backup tools that honor `CACHEDIR.TAG`

Caches in the Gradle user home can become very large and typically do not contain files that need to be searched or backed up.
The [CACHEDIR.TAG specification](https://bford.info/cachedir/) proposes a way for archive and backup tools to automatically skip these directories, which makes it unnecessary to explicitly exclude them.
Gradle now marks directories that should be ignored with a `CACHEDIR.TAG` file.

See [the userguide](userguide/directory_layout.html#dir:gradle_user_home:cache_marking) for more information on this feature.
See [the upgrading guide](userguide/upgrading_version_8.html#cache_marking) for details on disabling this if needed.

### IDE Integration

The following improvements are for IDE integrators.
They will be available for end-users in future IDE releases once they are used by IDE vendors.

#### Builds launched via the IDE use the same log level as the command-line

Previously, when executing a build via the IDE, the log level settings provided in the project's `gradle.properties` file were ignored.
Some IDE vendors worked around this shortcoming by setting the log level in other ways to meet user expectations.

The [Tooling API](userguide/third_party_integration.html#embedding) now honors the `org.gradle.logging.loglevel` setting in the project's `gradle.properties` and applies it as expected to builds started from the IDE.

Learn more about [changing log levels](userguide/logging.html#sec:choosing_a_log_level) in the user manual.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.

See the user manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Configuration cache

The configuration cache feature is now officially out of incubation and is ready for general use.
It is important to note, however, that utilizing this feature may require modifications to build logic and applied plugins to ensure full support.

The implementation of the configuration cache is not yet complete.
However, you can expect the configuration cache to evolve with new features and enhancements in upcoming Gradle versions, offering support for a broader range of use cases.
To explore the features currently under consideration for implementation, please refer to the [documentation](userguide/configuration_cache.html#config_cache:not_yet_implemented).

Enabling the configuration cache can yield a significant boost in build performance, as it caches the result of the configuration phase and reuses it for subsequent builds.
This means that subsequent builds no longer have to re-execute the configuration phase, resulting in faster and more efficient builds.

Configuration cache was introduced as an experimental feature back in [Gradle 6.6](https://docs.gradle.org/6.6/release-notes.html#configuration-caching).

To learn how to benefit from this feature, refer to the [Configuration Cache](userguide/configuration_cache.html) documentation.

See [the upgrading guide](userguide/upgrading_version_8.html#configuration_caching_options_renamed) if you were already using this feature in previous releases, 
as all `org.gradle.unsafe.configuration-cache...` properties were renamed to reflect the fact they are now fully supported.

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
