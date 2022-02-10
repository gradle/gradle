The Gradle team is excited to announce Gradle @version@.

This is the first patch release for Gradle 7.4.

It fixes the following issues:
* TODO Add fixed issues

We recommend users upgrade to 7.4.1 instead of 7.4.

----

This release makes it easier to create a [single test report](#aggregation-tests) or [JaCoCo code coverage report](#aggregation-jacoco) across several projects.
This release also includes several usability improvements, such as [marking additional test source directories as tests in IDEA](#idea-test-sources) and [better support for plugin version declarations in subprojects](#plugins-dsl).

[Java toolchain support](#java-toolchains) has been updated to reflect the [migration of AdoptOpenJDK to Adoptium](https://blog.adoptopenjdk.net/2021/03/transition-to-eclipse-an-update/).

There are changes to make adopting the [experimental configuration cache](#config-cache) easier, along with several [bug fixes](#fixed-issues) and [other changes](#other).

The [build services](userguide/build_services.html) and [version catalogs](userguide/platforms.html) features have been [promoted to stable](#promoted).

We would like to thank the following community members for their contributions to this release of Gradle:

[Michael Bailey](https://github.com/yogurtearl),
[Jochen Schalanda](https://github.com/joschi),
[Jendrik Johannes](https://github.com/jjohannes),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Konstantin Gribov](https://github.com/grossws),
[Per Lundberg](https://github.com/perlun),
[Piyush Mor](https://github.com/piyushmor),
[Róbert Papp](https://github.com/TWiStErRob),
[Piyush Mor](https://github.com/piyushmor),
[Ned Twigg](https://github.com/nedtwigg),
[Nikolas Grottendieck](https://github.com/Okeanos),
[Lars Grefer](https://github.com/larsgrefer),
[Patrick Pichler](https://github.com/patrickpichler),
[Marcin Mielnicki](https://github.com/platan),
[Marcono1234](https://github.com/Marcono1234),
[Dima Merkurev](https://github.com/dimorinny).
[Matthew Haughton](https://github.com/3flex)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<a name="aggregation-tests"></a>
### Generate a single report for tests from multiple projects

By default, Gradle produces a separate HTML test report for every test task in each project. Previously, it was difficult to combine those reports across multiple projects in a safe and convenient way.

This release adds the new [`test-report-aggregation`](userguide/test_report_aggregation_plugin.html) plugin to make it easy to aggregate test results from multiple projects into a single HTML report.
This plugin uses test suites registered with the [`jvm-test-suite`](userguide/jvm_test_suite_plugin.html) plugin.

When this plugin is applied to a Java project, Gradle will automatically create an aggregated test report for each test suite with test results from a compatible test suite in every project that the Java project depends on.
See [the sample](samples/sample_jvm_multi_project_with_test_aggregation_distribution.html) in the user manual.

If you want more control over the set of projects that are included in the aggregated report or which test suites are used, see [another sample that requires you to provide this configuration](samples/sample_jvm_multi_project_with_test_aggregation_standalone.html) in the user manual.

<a name="aggregation-jacoco"></a>
### Generate a single JaCoCo code coverage from multiple projects

Gradle comes with a [JaCoCo code coverage](userguide/jacoco_plugin.html) plugin that produces a coverage report for the built-in test task. Previously, it was difficult to combine such reports across multiple projects in a safe and convenient way.

This release adds the new [`jacoco-report-aggregation`](userguide/jacoco_report_aggregation_plugin.html) plugin to make it easy to aggregate code coverage from multiple projects into a single report.
This plugin uses test suites registered with the `jvm-test-suite` plugin.

When this plugin is applied to a Java project, Gradle will automatically create an aggregated code coverage report for each test suite with code coverage data from a compatible test suite in every project that the Java project depends on.
See [the sample](samples/sample_jvm_multi_project_with_code_coverage_distribution.html) in the user manual.

If you want more control over the set of projects that are included in the aggregated report or which test suites are used, see [another sample that requires you to provide this configuration](samples/sample_jvm_multi_project_with_code_coverage_standalone.html) in the user manual.

### Usability improvements

<a name="idea-test-sources"></a>
#### Mark additional test source directories as tests in IntelliJ IDEA

The [JVM Test Suite Plugin](userguide/jvm_test_suite_plugin.html) makes it easier to create additional sets of tests in a Java project.

The [IntelliJ IDEA Plugin](userguide/idea_plugin.html) plugin will now automatically mark all source directories used by a [JVM Test Suite](userguide/jvm_test_suite_plugin.html#declare_an_additional_test_suite) as test source directories within the IDE.

The Eclipse plugin will be updated in a future version of Gradle.

This change does not affect additional test source directories created in Android projects that will still need to be manually configured to be considered test directories.
These test sources are not created using JVM test suites.

#### Type-safe accessors for extensions of `repositories {}` in Kotlin DSL

Starting with this version of Gradle, [Kotlin DSL](userguide/kotlin_dsl.html) generates [type-safe model accessors](userguide/kotlin_dsl.html#type-safe-accessors) for custom extensions added to the `repositories {}` block.
Custom extensions now have full content assist in the IDE.

For instance, the [`asciidoctorj-gems-plugin`](https://asciidoctor.github.io/asciidoctor-gradle-plugin/master/user-guide/#asciidoctorj-gems-plugin) plugin adds a custom extension. You can now use this succinct syntax:
```kotlin
repositories {
    ruby {
        gems()
    }
}
```

In previous releases, you were required to use [`withGroovyBuilder`]():
```kotlin
repositories {
    withGroovyBuilder {
        "ruby" {
            "gems"()
        }
    }
}
```

or directly rely on the type of the extension:
```kotlin
repositories {
    this as ExtensionAware
    configure<com.github.jrubygradle.api.core.RepositoryHandlerExtension> {
        gems()
    }
}
```

#### Stable dependency verification file generation

[Dependency verification](userguide/dependency_verification.html) allows Gradle to verify the checksums and signatures of the plugins and dependencies that are used by the build of your project to improve supply chain security.

With this release, the generation of the dependency verification file has been improved to produce stable output.
This means that Gradle will always produce the same output if the build configuration and the verification file did not change.

This allows the [the verification metadata generation feature](userguide/dependency_verification.html#sec:bootstrapping-verification) to be used as a convenient update strategy.
This is now a recommended way to update the dependency verification files.

See [the documentation](userguide/dependency_verification.html#sec:verification-update) for more details.

<a name="plugins-dsl"></a>
#### Plugins can be declared with a version in a subproject in more cases

The [plugins DSL](userguide/plugins.html#sec:plugins_block) provides a succinct and convenient way to declare plugin dependencies.

Previously, it was not possible to declare a plugin with a version in a subproject when the parent project also declared the same plugin. Now, this is allowed when Gradle can track the version of the plugin (currently when using included build plugins or externally resolved plugins), and the version of the plugin in both are the same.

This change was required to allow the use of [`dependency catalog plugin aliases`](userguide/platforms.html#sec:plugins) in both a parent and subproject's `plugins {}`.

<a name="java-toolchains"></a>
### Changes following migration from AdoptOpenJDK to Adoptium

[Java toolchains](userguide/toolchains.html) provide an easy way to declare which Java version your project should be built with. By default, Gradle will [detect installed JDKs](userguide/toolchains.html#sec:auto_detection) or automatically download new toolchain versions.

Following the migration of [AdoptOpenJDK](https://adoptopenjdk.net/) to [Eclipse Adoptium](https://adoptium.net/), a number of changes have been made for toolchains:
* `ADOPTIUM` and `IBM_SEMERU` are now recognized as vendors,
* Both of the above can be used as vendors and trigger auto-provisioning,
* Using `ADOPTOPENJDK` as a vendor and having it trigger auto-provisioning will emit a [deprecation warning](userguide/upgrading_version_7.html#adoptopenjdk_download).

See [the documentation](userguide/toolchains.html#sec:provisioning) for details.

<a name="config-cache"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing this for subsequent builds.

#### Automatic detection of environment variables, system properties and Gradle properties used at configuration time

Previously, Gradle required build and plugin authors to use specific APIs to read external values such as environment variables, system properties and Gradle properties in order to take these values into consideration as configuration cache inputs. When one of those values changed, Gradle would re-execute the configuration phase of the build and create a new cache entry. Gradle also required marking external values used at configuration time with an explicit opt-in `Provider.forUseAtConfigurationTime()` API.

This release makes it easier to adopt configuration cache by relaxing these requirements. `Provider.forUseAtConfigurationTime()` has been deprecated and external values can be read using standard Java and Gradle APIs. Environment variables, system properties and Gradle properties used at configuration time are automatically detected without requiring build or plugin authors to migrate to Gradle specific APIs. In case any of those inputs change, the configuration cache is invalidated automatically. Moreover, the detected configuration inputs are now presented in the configuration-cache HTML report to make it easier to investigate unexpected configuration cache misses.

See the [corresponding section of the upgrade guide](userguide/upgrading_version_7.html#for_use_at_configuration_time_deprecation) for details.

#### Disable configuration caching when incompatible tasks are executed

The configuration cache works by caching the entire task graph for each set of requested tasks.

Prior to this release, all tasks used by the project needed to be compatible with configuration cache before the configuration cache could be enabled.

It is now possible to declare that a particular task is not compatible with the configuration cache.
Gradle will disable the configuration cache automatically whenever an incompatible task is scheduled to run.
This makes it possible to enable the configuration cache without having to first migrate all tasks to be compatible.
Builds will still benefit from the configuration cache when only compatible tasks are executed.
This enables more gradual adoption of the configuration cache.

Check the [user manual](userguide/configuration_cache.html#config_cache:task_opt_out) for more details.

<a name="other"></a>
### Other improvements

#### Additional Gradle daemon debug options

The Gradle daemon can be started in a debug mode that allows you to connect a debugger to troubleshoot build scripts or plugin code execution.
By default, Gradle assumes a particular set of debugging options.

In this release, additional options have been added to specify the port, server mode, and suspend mode for the Gradle daemon.
This is useful when the default options are not sufficient to connect to the Gradle daemon to debug build scripts or plugin code.

This **does not** affect the debugging options used with `--debug-jvm` when used with `Test` or `JavaExec` tasks.

See [the documentation](userguide/command_line_interface.html#sec:command_line_debugging) for details.

This improvement was contributed by [Marcin Mielnicki](https://github.com/platan).

#### Conflicts of precompiled plugins with core plugins is now an error

[Precompiled plugins](userguide/custom_plugins.html#sec:precompiled_plugins) are plugins written in either Groovy or Kotlin DSLs. They are used to easily share build logic between projects using a DSL language and without the need to write a full plugin class.

In previous versions, it was possible to name a precompiled plugin with a name that conflicted with any core plugin id.
Since core plugins take precedence over other plugins this caused the precompiled plugin to be silently ignored.

With this release, a name conflict between a precompiled plugin and a core plugin causes an error.

See the user manual for [precompiled plugins](userguide/custom_plugins.html#sec:precompiled_plugins) for more information.

<a name="promoted"></a>
## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Shared build services

[Shared build services](userguide/build_services.html) is promoted to a stable feature.

### Version catalogs

[Version catalogs](userguide/platforms.html) is promoted to a stable feature.

## Fixed issues

### Idle Connection Timeout

Some CI hosting providers like Azure automatically close idle connections after a certain period of time.

This caused problems with connections to the Gradle Build Cache which could have an open connection for the entire execution of the build.

This release of Gradle fixes this issue by automatically closing idle connections after 3 min by default.

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
