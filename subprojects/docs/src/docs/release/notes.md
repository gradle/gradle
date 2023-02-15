The Gradle team is excited to announce a new major version of Gradle, 8.0.

This release reduces the time spent compiling [Kotlin DSL build scripts](#kotlin-dsl) and upgrades the API level to [Kotlin 1.8](https://kotlinlang.org/docs/whatsnew18.html).

The [incubating configuration cache](#configuration-cache) speeds up builds by executing more tasks in parallel on the first build than enabling `--parallel` alone.

Additionally, the size of the Gradle user home can be managed by [configuring the retention time](#cache-cleanup) of cache directories. Cache cleanup can also be disabled.

Gradle 8.0 has many bug fixes and other [general improvements](#general-improvements).
As a major version, this release also has changes to deprecated APIs and behavior. 
Consult the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) for guidance on removed APIs and behavior.  

We would like to thank the following community members for their contributions to this release of Gradle:
[Abdul Rauf](https://github.com/armujahid),
[Andrei Nevedomskii](https://github.com/monosoul),
[aSemy](https://github.com/aSemy),
[Ben Cox](https://github.com/ind1go),
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
[Hyeonmin Park](https://github.com/KENNYSOFT),
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
[Siarhei](https://github.com/madhead),
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

## New features, performance and usability improvements

<a name="kotlin-dsl"></a>
### Kotlin DSL

Gradle's [Kotlin DSL](userguide/kotlin_dsl.html) provides an alternative syntax to the Groovy DSL with an enhanced editing experience in supported IDEs--superior content assistance, refactoring, documentation and more.

#### Faster Kotlin DSL build script compilation

Gradle 8.0 introduces an interpreter for the [declarative `plugins {}` block](userguide/plugins.html#sec:constrained_syntax) in `.gradle.kts` scripts that make overall script compilation time around 20% faster.

The performance improvement is limited in some scenarios.
If the interpreter cannot parse the declarative `plugins {}` block, Gradle will fallback to using the Kotlin compiler. 
You need to be using only supported formats, for example:

```kotlin
plugins {
    id("java-library")                                // <1>
    id("com.acme.example") version "1.0" apply false  // <2>
    kotlin("jvm") version "1.7.21"                    // <3>
}
```
1. Plugin requested by plugin identifier string
2. Plugin requested by plugin identifier and version and/or the plugin apply flag
3. Plugin requested with the `kotlin(...)` helper

Note that using version catalog aliases for plugins (e.g. `alias(libs.plugins.acme)`) or type-safe plugin accessors (e.g. `` `acme-plugin` ``) will not see a performance improvement. Support for these formats will be added later.

For more information on plugin syntax, read the documentation on the [constrained syntax](userguide/plugins.html#sec:constrained_syntax) of the declarative `plugins {}` block.

#### Kotlin DSL updated to Kotlin API level 1.8

Previously, the Kotlin DSL was limited to Kotlin API level 1.4. Starting with Gradle 8.0, the Kotlin DSL is fixed to [Kotlin API level 1.8](https://kotlinlang.org/docs/whatsnew18.html). This change brings all the improvements made to the Kotlin language and standard library since Kotlin 1.4.0 to Kotlin DSL build scripts.

For information about breaking and non-breaking changes in this upgrade, visit the [upgrading 7.x guide](userguide/upgrading_version_7.html#kotlin_language_1_8).

#### Kotlin DSL can use newer Java features

Previously, the compilation of `.gradle.kts` scripts was limited to Java 8 bytecode and features. Starting with Gradle 8.0, Kotlin DSL will use the Java version of the JVM running the build.

If your team is using Java 11 to run Gradle, you can now use Java 11 libraries and language features in your build scripts.

Note that this doesn't apply to precompiled script plugins, see below.

##### Precompiled script plugins use the configured Java Toolchain

Previously, the compilation of [precompiled script plugins](userguide/custom_plugins.html#sec:precompiled_plugins) used the JVM target as configured by `kotlinDslPluginOptions.jvmTarget`.

Starting with Gradle 8.0, precompiled script plugins use the configured [Java Toolchain](userguide/toolchains.html) for the project or Java 8 if no toolchain is configured.

See the [`kotlin-dsl` plugin manual](userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin) for more information on how to configure the Java Toolchain for precompiled script plugins and the [migration guide](userguide/upgrading_version_7.html#kotlin_dsl_plugin_toolchains) for more information on changed behavior.

<a name="improvements-for-buildsrc-builds"></a>
### Improvements for `buildSrc` builds

This release includes several improvements for [`buildSrc`](userguide/organizing_gradle_projects.html#sec:build_sources) builds to behave more like [included builds](userguide/composite_builds.html). Included builds are an alternative way to organize your build logic to separate project configurations to better utilize incremental builds and task caching. Now they offer the same benefits.

#### Run `buildSrc` tasks directly

It is now possible to run the tasks of a `buildSrc` build from the command-line, using the same syntax used for tasks of included builds.
For example, you can use `gradle buildSrc:build` to run the `build` task in the `buildSrc` build.

For more details, see the [user manual](userguide/composite_builds.html#composite_build_executing_tasks).

#### `buildSrc` can include other builds

The `buildSrc` build can now include other builds by declaring them in `buildSrc/settings.gradle.kts` or `buildSrc/settings.gradle`.  This allows you to better orgaize your build logic while still using `buildSrc`.

You can use `pluginsManagement { includeBuild(someDir) }` or `includeBuild(someDir)` in this settings script to include other builds in `buildSrc`.

For more details, see the [user manual](userguide/composite_builds.html).

#### Tests for `buildSrc` are no longer automatically run

When Gradle builds the output of `buildSrc` it only runs the tasks that produce that output. It no longer runs the `build` task. In particular, this means that the tests of `buildSrc` and its subprojects are not built and executed when they are not needed.

You can run the tests for `buildSrc` in the same way as other projects, as described above.

#### Init scripts are applied to `buildSrc`

Init scripts specified on the command-line using `--init-script` are now applied to `buildSrc`, in addition to the main build and all included builds.

For more details, see the [user manual](userguide/init_scripts.html).

<a name="configuration-cache"></a>
### Configuration cache

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing this for subsequent builds. This is an incubating feature that can significantly improve build performance.

#### More parallelism on the first build

Configuration cache now enables more fine-grained parallelism than just enabling [parallel execution](userguide/multi_project_configuration_and_execution.html#sec:parallel_execution).
Starting in Gradle 8.0, tasks run in parallel from the first build when using the configuration cache.

Gradle has always run tasks in parallel when it reuses a [configuration cache](userguide/configuration_cache.html) entry. All tasks run in parallel by default, even those within the same project, subject to dependency constraints.  Now, it does this also when storing a cache entry.

When the configuration cache is enabled and Gradle is able to find a compatible cache entry for the current build, it will load the tasks from the cache and run them in isolation. If Gradle cannot find a suitable cache entry, it will run the configuration phase to determine the necessary tasks, store them in a new cache entry, and then immediately run the build based on the saved state.

This new behavior has several benefits:

- Any issues that occur during deserialization will be easier to detect because they will be reported in the cache miss build. 
- Tasks have the same state in both cache miss and cache hit builds, allowing for consistency between builds.
- Gradle can release memory used by the configuration state before task execution in the cache miss build, which reduces peak memory usage.

This consistent behavior between cache miss and cache hit builds will help those who are transitioning to using the configuration cache, as more problems can be discovered on the first (cache miss) build.

For more details, see the [user manual](userguide/configuration_cache.html).

#### Expanded compatibility with core plugins

The [`gradle init` command](userguide/build_init_plugin.html) can be used with the configuration cache enabled.

The [ANTLR plugin](userguide/antlr_plugin.html) and [Groovy DSL precompiled scripts](userguide/custom_plugins.html#sec:precompiled_plugins) are now compatible with the configuration cache.

The current status of the configuration cache support for all core Gradle plugins can be found in the [configuration cache documentation](userguide/configuration_cache.html#config_cache:plugins).

<a name="java-toolchains"></a>
### Java Toolchains improvements

#### Updated toolchain download repositories

Gradle 7.6 introduced [toolchain repositories](userguide/toolchains.html#sub:download_repositories) for increased flexibility. In Gradle 8.0, there is no longer a default toolchain provisioner. You have to declare at least one Java Toolchain repository explicitly. This can be done via toolchain repository plugins, like the [Foojay Toolchains Plugin](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention):

```
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}
```

For more details, see the [user manual](userguide/toolchains.html#sec:provisioning).

<a name="general-improvements"></a>
### General improvements

<a name="cache-cleanup"></a>
#### Configurable Gradle user home cache cleanup and retention

Previously, cleanup of the caches in Gradle user home used fixed retention periods (30 days or 7 days, depending on the cache). These retention periods can now be configured via the [settings](dsl/org.gradle.api.initialization.Settings.html) object in an init script in Gradle user home.

```groovy
beforeSettings { settings ->
    settings.caches {
        downloadedResources.removeUnusedEntriesAfterDays = 45
    }
}
```

Furthermore, it was previously only possible to partially disable cache cleanup via the `org.gradle.cache.cleanup` Gradle property in Gradle user home. Disabling cache cleanup now affects more caches under Gradle user home and can also be configured via the [settings](dsl/org.gradle.api.initialization.Settings.html) object in an init script in Gradle user home.

For more details, see the [user manual](userguide/directory_layout.html#dir:gradle_user_home:configure_cache_cleanup).

#### Enhanced warning modes `all` and `fail` are now more verbose

Before Gradle 8.0, warning modes that were supposed to print all warnings were printing only one for each specific warning message. This resulted in some missing warning messages. For example, if there were two warnings with the same message, but originating from different steps of the build process (i.e., different stack traces), only one was printed.

Now one gets printed for each combination of message and stack trace. This result is more verbose, but also more complete.

For more details, see the [user manual](userguide/command_line_interface.html#sec:command_line_warnings).

#### Dependency verification metadata supports documenting reasons for trust

[Dependency verification metadata](userguide/dependency_verification.html#sub:verification-metadata) helps keep your project secure by ensuring the dependency being used matches the checksum of that dependency. This metadata is located in an XML configuration file and now accepts a reason attribute. This reason attribute allows for more details on why an artifact is trusted or why a selected checksum verification is required for an artifact directly in the `verification-metadata.xml`.

The following nodes with dependency verification metadata file `verification-metadata.xml` now support a `reason` attribute:

- the `trust` xml node under `trusted-artifacts`
- the `md5`, `sha1`, `sha256` and `sha512` nodes under `component`

A reason is helpful to provide more details on why an artifact is trusted or why a selected checksum verification is required for an artifact directly in the `verification-metadata.xml`.

For more details, see the [user manual](userguide/dependency_verification.html#sub:verification-metadata).

#### Trusted keyring files can be exported easily from the CLI

To minimize the number of times CI builds need to communicate with key servers, Gradle supports a [local keyring file](userguide/dependency_verification.html#sec:local-keyring-only). This file needs to be frequently exported to remain accurate. It is no longer required to write the full verification metadata when exporting trusted keys.

You can now use the `export-keys` flag to export all already trusted keys:
```asciidoc
./gradlew --export-keys
```

For more details, see the [user manual](userguide/dependency_verification.html#sec:local-keyring).

<a name="code-quality"></a>
### Code quality plugin improvements

#### CodeNarc plugin detects the Groovy runtime version

[CodeNarc](https://codenarc.org/) performs static analysis for Groovy projects. It now publishes separate versions for use with Groovy 4. Gradle still currently ships with Groovy 3.

To ensure future compatibility, the [CodeNarc Plugin](userguide/codenarc_plugin.html) now automatically detects the appropriate version of CodeNarc for the current Groovy runtime.

You can still explicitly specify a CodeNarc version with the `toolVersion` property on the [CodeNarcExtension](dsl/org.gradle.api.plugins.quality.CodeNarcExtension.html#org.gradle.api.plugins.quality.CodeNarcExtension).

#### Faster PMD analysis with parallel execution by default

The [PMD](userguide/pmd_plugin.html) plugin performs quality checks on your project’s Java source files using a static code analyzer. It now uses the Gradle [worker API](userguide/custom_tasks.html#worker_api) and [JVM toolchains](userguide/toolchains.html#header). This tool now performs analysis via an external worker process, and therefore its tasks may now run in parallel within one project.

In Java projects, this tool will use the same version of Java required by the project. In other types of projects, it will use the same version of Java that is used by the Gradle daemon.

For more details, see the [user manual](userguide/pmd_plugin.html).

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

### Promoted features in the Tooling API

The `GradleConnector.disconnect()` method is now considered stable.

### Promoted features in the antlr plugin

The `AntlrSourceDirectorySet` interface is now considered stable.

### Promoted features in the ear plugin

The `Ear.getAppDirectory()` method is now considered stable.

### Promoted features in the eclipse plugin

The `EclipseClasspath.getContainsTestFixtures()` method is now considered stable.

### Promoted features in the groovy plugin

The following type and method are now considered stable:

- `GroovySourceDirectorySet`
- `GroovyCompileOptions.getDisabledGlobalASTTransformations()`

### Promoted features in the scala plugin

The `ScalaSourceDirectorySet` interface is now considered stable.

### Promoted features in the war plugin

The `War.getWebAppDirectory()` method is now considered stable.

### Promoted features in the `Settings` API

The methods `Settings.dependencyResolutionManagement(Action)` and `Settings.getDependencyResolutionManagement()` are now considered stable.

All the methods in `DependencyResolutionManagement` are now stable, except the ones for central repository declaration.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure that you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
