
The Gradle team is excited to announce a new major version of Gradle, 8.0. This major version has breaking changes; please consult the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) for guidance.

This release includes several improvements to the [Kotlin DSL](#kotlin-dsl) for increased performance. This includes an interpreter to skip the Kotlin compiler for declarative plugins and an upgrade to Kotlin API level 1.8.

The benefits of using included builds are now available with [`buildSrc`](#improvements-for-buildsrc-builds), such as running `buildSrc` tasks directly, skipping tests, and including other builds with `buildSrc`. 

Additionally, we have made enhancements to the [configuration cache](#configuration-cache), like loading tasks from the cache entry and running tasks as isolated and in parallel on the first build, along with several bug fixes and other [general improvements](#general-improvements).

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
 The list is rendered as is, so use commas after each contributor's name, and a period at the end.
-->

We would like to thank the following community members for their contributions to this release of Gradle:
[Abdul Rauf](https://github.com/armujahid),
[Andrei Nevedomskii](https://github.com/monosoul),
[aSemy](https://github.com/aSemy),
[Björn Kautler](https://github.com/Vampire),
[bodhili](https://github.com/bodhili),
[Cédric Champeau](https://github.com/melix),
[Christoph Dreis](https://github.com/dreis2211),
[Clara Guerrero Sánchez](https://github.com/cguerreros),
[David Marin Vaquero](https://github.com/dmarin),
[David Morris](https://github.com/codefish1),
[Denis Buzmakov](https://github.com/bacecek),
[Dmitry Pogrebnoy](https://github.com/DmitryPogrebnoy),
[Dzmitry Neviadomski](https://github.com/nevack),
[Eliezer Graber](https://github.com/eygraber),
[Eric Pederson](https://github.com/sourcedelica),
[Fedor Ihnatkevich](https://github.com/Jeffset),
[Gabriel Rodriguez](https://github.com/gabrielrodriguez2746),
[Herbert von Broeuschmeul](https://github.com/HvB),
[Jeff](https://github.com/mathjeff),
[Jendrik Johannes](https://github.com/jjohannes),
[Korov](https://github.com/Korov),
[Marcono1234](https://github.com/Marcono1234),
[Mariell Hoversholm](https://github.com/Proximyst),
[Matthew Haughton](https://github.com/3flex),
[Matthias Ernst](https://github.com/mernst-github),
[Michael Ernst](https://github.com/mernst),
[Michael Torres](https://github.com/torresmi),
[Pankaj](https://github.com/p1729),
[prasad-333](https://github.com/prasad-333),
[RicardoJiang](https://github.com/RicardoJiang),
[Róbert Papp](https://github.com/TWiStErRob),
[Siddardha Bezawada](https://github.com/SidB3),
[Stephen Topley](https://github.com/stopley),
[Victor Maldonado](https://github.com/vmmaldonadoz),
[Vinay Potluri](https://github.com/vinaypotluri),
[Xin Wang](https://github.com/scaventz).


## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

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


<a name="kotlin-dsl"></a>
### Kotlin DSL

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an alternative syntax to the traditional Groovy DSL with an enhanced editing experience in supported IDEs, with superior content assistance, refactoring, documentation, and more.

#### Improved script compilation performance

Gradle 8.0 introduces an interpreter for the [declarative plugins {} blocks](userguide/plugins.html#sec:constrained_syntax) in `.gradle.kts` scripts that make the overall build time around 20% faster. Calling the Kotlin compiler for declarative `plugins {}` blocks is avoided by default.

To utilize this performance, ensure you are using the supported formats in the declarative `plugins {}` blocks, for example:

```kotlin
plugins {
    id("java-library")                               // <1>
    id("com.acme.example") version "1.0" apply false  // <2>
    kotlin("jvm") version "1.7.21"                   // <3>
}
```
1. Plugin specification by plugin identifier string
2. Plugin specification with version and/or the plugin application flag
3. Kotlin plugin specification helper

Note that using version catalog aliases for plugins or plugin specification type-safe accessors is not supported by the `plugins {}` block interpreter. This support will be added in a later version.

In unsupported cases, Gradle falls back to the Kotlin compiler, providing the same performance as previous Gradle releases.

For more information on plugin syntax, read the documentation on [constrained syntax](userguide/plugins.html#sec:constrained_syntax).

#### Updated the Kotlin DSL to Kotlin API level 1.8

Previously, the Kotlin DSL used Kotlin API level 1.4. Starting with Gradle 8.0, the Kotlin DSL uses Kotlin API level 1.8. This change brings all the improvements made to the Kotlin language and standard library since Kotlin 1.4.0.

For information about breaking and non-breaking changes in this upgrade, visit the [upgrading guide](userguide/upgrading_version_7.html#kotlin_language_1_8).

#### Enhanced script compilation to use the Gradle JVM as Kotlin JVM target

If your team is using e.g., Java 11 to run Gradle, this allows you to use Java 11 libraries and language features in your build scripts.

Previously, the compilation of `.gradle.kts` scripts always used Java 8 as the Kotlin JVM target. Starting with Gradle 8.0, it now uses the version of the JVM running the build.

Note that this doesn't apply to precompiled script plugins (below).

##### Precompiled script plugins now use the configured Java Toolchain

Previously, the compilation of [precompiled script plugins](userguide/custom_plugins.html#sec:precompiled_plugins) used the JVM target as configured on `kotlinDslPluginOptions.jvmTarget`.
Starting with Gradle 8.0, it now uses the configured Java Toolchain, or Java 8 if none is configured.

See the [`kotlin-dsl` plugin manual](userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin) for more information on how to configure the Java Toolchain for precompiled script plugins and the [migration guide](userguide/upgrading_version_7.html#kotlin_dsl_plugin_toolchains) for more information on changed behaviour.

<a name="improvements-for-buildsrc-builds"></a>
### Improvements for `buildSrc` builds

This release includes several improvements for [`buildSrc`](userguide/organizing_gradle_projects.html#sec:build_sources) builds to behave more like [included builds](userguide/composite_builds.html). Included builds are an alternative way to organize your build logic to separate project configurations to better utilize incremental builds and task caching. Now they offer the same benefits. 

#### Run `buildSrc` tasks directly

It is now possible to run the tasks of a `buildSrc` build from the command-line, using the same syntax used for tasks of included builds.
For example, you can use `gradle buildSrc:build` to run the `build` task in the `buildSrc` build.

For more details, see the [user manual](userguide/composite_builds.html#composite_build_executing_tasks)

#### `buildSrc` can include other builds

The `buildSrc` build can now include other builds by declaring them in `buildSrc/settings.gradle.kts` or `buildSrc/settings.gradle`.
You can use `pluginsManagement { includeBuild(someDir) }` or `includeBuild(someDir)` in this settings script to include other builds in `buildSrc`.

For more details, see the [user manual](userguide/composite_builds.html)

#### Tests for `buildSrc` are no longer automatically run

When Gradle builds the output of `buildSrc` it only runs the tasks that produce that output. It no longer runs the `build` task. In particular, this means that the tests of `buildSrc` and its subprojects are not built and executed when they are not needed.

You can run the tests for `buildSrc` in the same way as other projects, as described above.

#### Init scripts are applied to `buildSrc`

Init scripts specified on the command-line using `--init-script` are now applied to `buildSrc`, in addition to the main build and all included builds.

For more details, see the [user manual](userguide/composite_builds.html#composite_build_executing_tasks)

<a name="configuration-cache"></a>
### Configuration cache

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing this for subsequent builds. This is an incubating feature that can significantly improve build performance. 

#### Improved configuration cache for parallelism on the first run

Configuration cache now enables more fine-grained parallelism than using the --parallel flag. Starting in Gradle 8.0, tasks run in parallel from the first build when using the configuration cache. These tasks are isolated and can run in parallel. 

When the [configuration cache](userguide/configuration_cache.html) is enabled and Gradle can locate a compatible configuration cache entry for the requested tasks, it loads the tasks to run from the cache entry and runs them in isolation. Isolated tasks can run in parallel by default, subject to dependency constraints.

When Gradle cannot locate a configuration cache entry to use, it runs the configuration phase to calculate the set of tasks to run and then stores these tasks in a new cache entry. There are some additional advantages to this new behavior:

- Any problems that happen during deserialization will be reported in the cache miss build, making it easier to spot such problems.
- Tasks have access to the same state in cache miss and cache hit builds.
- Gradle can release all memory used by the configuration state before task execution in the cache miss build. Previously it would retain this state because the non-isolated tasks could access it.
- This reduces the peak memory usage for a given set of tasks.

This consistent behavior for cache miss and cache hit builds can help people migrating to use the configuration cache, as more problems can now be discovered on the first (cache miss) build.

For more details, see the [user manual](userguide/configuration_cache.html).

#### Improved compatibility with core plugins

The [`gradle init` command](userguide/build_init_plugin.html) can be used with the configuration cache enabled.

The [ANTLR plugin](userguide/antlr_plugin.html) and [Groovy DSL precompiled scripts](userguide/custom_plugins.html#sec:precompiled_plugins) are now compatible with the configuration cache.

The current status of the configuration cache support for all core Gradle plugins can be found in the [configuration cache documentation](userguide/configuration_cache.html#config_cache:plugins).

<a name="java-toolchains"></a>
### Java Toolchains improvements

##### Updated toolchain download repositories

Gradle 7.6 introduced [toolchain repositories](userguide/toolchains.html#sub:download_repositories) for increased flexibility. In Gradle 8.0, there is no longer a default toolchain provisioner. You have to declare at least one Java Toolchain repository explicitly. This can be done via toolchain repository plugins, like the [Foojay Toolchains Plugin](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention):

```
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.3.0")
}
```

For more information, see [Toolchain Download Repositories](userguide/toolchains.html#sec:provisioning).

<a name="general-improvements"></a>
### General Improvements

#### Improved Gradle user home cache cleanup

Previously, cleanup of the caches in Gradle user home used fixed retention periods (30 days or 7 days, depending on the cache). These retention periods can now be configured via the [settings](dsl/org.gradle.api.initialization.Settings.html) object in an init script in Gradle user home.

```groovy
beforeSettings { settings ->
    settings.caches {
        downloadedResources.removeUnusedEntriesAfterDays = 45
    }
}
```

Furthermore, it was previously only possible to partially disable cache cleanup via the `org.gradle.cache.cleanup` Gradle property in Gradle user home. Disabling cache cleanup now affects more caches under Gradle user home and can also be configured via the [settings](dsl/org.gradle.api.initialization.Settings.html) object in an init script in Gradle user home.

See [Configuring cleanup of caches and distributions](userguide/directory_layout.html#dir:gradle_user_home:configure_cache_cleanup) for more information.

#### Enhanced warning modes `all` and `fail` are now more verbose

Before Gradle 8.0, warning modes that were supposed to print all warnings were printing only one for each specific warning message. This resulted in some missing warning messages. For example, if there were two warnings with the same message, but originating from different steps of the build process (i.e., different stack traces), only one was printed.

Now one gets printed for each combination of message and stack trace. This result is more verbose, but also more complete.

For more information about warning modes, see [showing or hiding warnings](userguide/command_line_interface.html#sec:command_line_warnings).

#### Improved dependency verification metadata

[Dependency verification metadata](userguide/dependency_verification.html#sub:verification-metadata) helps keep your project secure by ensuring the dependency being used matches the checksum of that dependency. This metadata is located in an XML configuration file and now accepts a reason attribute. This reason attribute allows for more details on why an artifact is trusted or why a selected checksum verification is required for an artifact directly in the `verification-metadata.xml`.

The following nodes with dependency verification metadata file `verification-metadata.xml` now support a `reason` attribute:

- the `trust` xml node under `trusted-artifacts`
- the `md5`, `sha1`, `sha256` and `sha512` nodes under `component`

A reason is helpful to provide more details on why an artifact is trusted or why a selected checksum verification is required for an artifact directly in the `verification-metadata.xml`.

For more details, see the [user manual](userguide/dependency_verification.html#sub:verification-metadata).

#### Improved dependency verification CLI

To minimize the number of times CI builds communicate with key servers, we use a [local keyring file](userguide/dependency_verification.html#sec:local-keyring-only). This file should be frequently exported to remain accurate. There is no longer a need to write verification metadata when exporting trusted keys, simplifying the process.

You can now use the `export-keys` flag to export all already trusted keys:

```asciidoc
./gradlew --export-keys
```

There is no longer a need to write verification metadata when exporting trusted keys.

For more information, see [exporting keys](userguide/dependency_verification.html#sec:local-keyring).

<a name="code-quality"></a>
### Code quality plugin improvements

#### CodeNarc plugin detects the Groovy runtime version

The [CodeNarc](https://codenarc.org/) performs static analysis for Groovy projects. It now publishes separate versions for use with Groovy 4. Gradle still currently ships with Groovy 3.

To ensure future compatibility, the [CodeNarc Plugin](userguide/codenarc_plugin.html) now automatically detects the appropriate version of CodeNarc for the current Groovy runtime.

You can still explicitly specify a CodeNarc version with the `toolVersion` property on the [CodeNarcExtension](dsl/org.gradle.api.plugins.quality.CodeNarcExtension.html#org.gradle.api.plugins.quality.CodeNarcExtension).

#### PMD and CodeNarc tasks now execute in parallel by default

The [PMD](userguide/pmd_plugin.html)  plugin performs quality checks on your project’s Java source files using a static code analyzer. It now uses the Gradle worker API and JVM toolchains. This tool now performs analysis via an external worker process, and therefore its tasks may now run in parallel within one project.

In Java projects, this tool will use the same version of Java required by the project. In other types of projects, it will use the same version of Java that is used by the Gradle daemon.

For more details, see the [user manual](userguide/pmd_plugin.html).

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Promoted features in the Tooling API

- The `GradleConnector.disconnect()` method is now considered stable.

### Promoted features in the antlr plugin

- The `AntlrSourceDirectorySet` interface is now considered stable.

### Promoted features in the ear plugin

- The `Ear.getAppDirectory()` method is now considered stable.

### Promoted features in the eclipse plugin

- The `EclipseClasspath.getContainsTestFixtures()` method is now considered stable.

### Promoted features in the groovy plugin

The following type and method are now considered stable:
- `GroovySourceDirectorySet`
- `GroovyCompileOptions.getDisabledGlobalASTTransformations()`

### Promoted features in the scala plugin

- The `ScalaSourceDirectorySet` interface is now considered stable.

### Promoted features in the war plugin

- The `War.getWebAppDirectory()` method is now considered stable.

### Promoted features in the `Settings` API

- The methods `Settings.dependencyResolutionManagement(Action)` and `Settings.getDependencyResolutionManagement()` are now considered stable.

- All the methods in `DependencyResolutionManagement` are now stable, except the ones for central repository declaration.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
