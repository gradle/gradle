<meta property="og:image" content="https://gradle.org/assets/images/releases/gradle-default.png" />
<meta property="og:type"  content="article" />
<meta property="og:title" content="Gradle @version@ Release Notes" />
<meta property="og:site_name" content="Gradle Release Notes">
<meta property="og:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:site" content="@gradle">
<meta name="twitter:creator" content="@gradle">
<meta name="twitter:title" content="Gradle @version@ Release Notes">
<meta name="twitter:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:image" content="https://gradle.org/assets/images/releases/gradle-default.png">

We are excited to announce Gradle @version@ (released [@releaseDate@](https://gradle.org/releases/)).

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew :wrapper --gradle-version=@version@ && ./gradlew :wrapper
```

See the [Gradle 9.x upgrade guide](userguide/upgrading_version_9.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

<!-- ================== TEMPLATE =============================

Do not add breaking changes or deprecations here! Add them to the upgrade guide instead.

Find the best fitting section for your feature below, then, fill it in.

### SECTION TITLE

#### FILL-IN-FEATURE
> HIGHLIGHT the use case or existing problem the feature solves.
> EXPLAIN how the new release addresses that problem or use case.
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable.
> LINK to the full documentation for more details.

To embed images, add the image to the `release-notes-assets` folder, then add the line below.
![image.png](release-notes-assets/image.png)

To embed videos, use the macros below. 
You can extract the URL from YouTube by clicking the "Share" button.
@youtube(Summary,6aRM8lAYyUA?si=qeXDSX8_8hpVmH01)@

================== END TEMPLATE ========================== -->


<!-- =========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

### Support for Java 27

With this release, Gradle supports [Java 27](https://openjdk.org/projects/jdk/27/).

You can now run the [Gradle daemon](userguide/gradle_daemon.html) on Java 27, in addition to using it via [toolchains](userguide/toolchains.html):

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
    }
}
```

Some third-party tools (for example, PMD) do not yet support Java 27.

See [the compatibility documentation](userguide/compatibility.html#java_runtime) for more details.

### Configuration Cache improvements
Gradle provides a [Configuration Cache](userguide/configuration_cache.html) that improves build time by caching the result of the configuration phase and reusing it for subsequent builds.

#### TestKit API for asserting the Configuration Cache outcome

Plugin authors testing Configuration Cache compatibility with [TestKit](userguide/test_kit.html) previously had to parse console output to tell whether a cache entry was stored, reused, or discarded — an approach that broke whenever the wording of the console messages changed.

The [`BuildResult`](javadoc/org/gradle/testkit/runner/BuildResult.html) now exposes the outcome directly:

```groovy
def result = GradleRunner.create()
    .withProjectDir(projectDir)
    .withArguments("myTask", "--configuration-cache")
    .build()

assert result.configurationCacheOutcome == ConfigurationCacheOutcome.STORED
```

See [`ConfigurationCacheOutcome`](javadoc/org/gradle/testkit/runner/ConfigurationCacheOutcome.html) for the possible outcomes.

The outcome is also available to any Tooling API client through a new [`CONFIGURATION_CACHE`](javadoc/org/gradle/tooling/events/OperationType.html#CONFIGURATION_CACHE) progress event type.

See the [Testing with the Configuration Cache](userguide/test_kit.html#sub:test-kit-configuration-cache) section in the Gradle User Manual for more details.

### Test reporting and execution
Gradle provides a [set of features and abstractions](userguide/java_testing.html) for testing JVM code, along with test reports to display results.

### CLI, logging, and problem reporting
Gradle provides an intuitive [command-line interface](userguide/command_line_interface.html), detailed [logs](userguide/logging.html), and a structured [problems report](userguide/reporting_problems.html#sec:generated_html_report) that helps developers quickly identify and resolve build issues.

### Build authoring improvements
Gradle provides [rich APIs](userguide/getting_started_dev.html) for build engineers and plugin authors, enabling the creation of custom, reusable build logic and better maintainability.

#### Look up Gradle services from scripts and task actions

Build, settings, and init scripts, as well as task actions, can now [look up commonly used Gradle services](userguide/service_injection.html#looking_up_services) directly, without declaring an `@Inject` point or going through the `objects.newInstance(...)` ceremony:

```kotlin
tasks.register("cleanReports") {
    val fs = service<FileSystemOperations>()
    doLast {
        fs.delete { delete("build/reports") }
    }
}
```

Use `service(Class)` in the Groovy DSL or `service<Type>()` in the Kotlin DSL. It is compatible with the Configuration Cache and Isolated Projects, and covers `ObjectFactory`, `ProviderFactory`, `FileSystemOperations`, and `ArchiveOperations` in every scope, `ProjectLayout` in projects and tasks, `BuildLayout` in settings, and `ExecOperations` in task actions. In the Kotlin and Java DSLs, looking up a service that is not available in the current scope is caught at compile time.

Precompiled script plugins that use `service<Type>()` require Gradle 9.8 or later at runtime.

See the [Looking up services in scripts](userguide/service_injection.html#looking_up_services) section in the Gradle User Manual for more details.

#### Lazy destination directory for `Copy` and `Sync`

The [`Copy`](dsl/org.gradle.api.tasks.Copy.html) and [`Sync`](dsl/org.gradle.api.tasks.Sync.html) tasks only exposed the destination as `destinationDir`, a plain `File` property, so a destination derived from a provider had to be resolved eagerly at configuration time.
Both tasks now expose a `destinationDirectory` [`DirectoryProperty`](javadoc/org/gradle/api/file/DirectoryProperty.html):

```kotlin
tasks.register<Copy>("copyFiles") {
    from("src")
    destinationDirectory = layout.buildDirectory.dir("out")
}
```

The property is the single source of truth for the destination.
Assigning it is equivalent to calling `into(...)`, and it reflects whatever was configured through `into(...)` or `destinationDir`.

`into(...)` now wires a `Provider` destination into `destinationDirectory` instead of resolving its value, so provider-based destinations stay lazy; all other notations (`String`, `File`, `Closure`, `Callable`, ...) keep their existing lazy resolution.
Since `destinationDirectory` is a task output property, other tasks can consume it directly and pick up the task dependency.

The new property is [incubating](userguide/feature_lifecycle.html#feature_preview). `destinationDir` continues to work and will be deprecated once `destinationDirectory` is promoted.

See [`Copy.destinationDirectory`](dsl/org.gradle.api.tasks.Copy.html#org.gradle.api.tasks.Copy:destinationDirectory) and [`Sync.destinationDirectory`](dsl/org.gradle.api.tasks.Sync.html#org.gradle.api.tasks.Sync:destinationDirectory) in the DSL Reference for more details.
#### `Sync` now deletes stale outputs when its source becomes empty

Previously, a [`Sync`](javadoc/org/gradle/api/tasks/Sync.html) task with no source files was reported as `NO-SOURCE` and skipped, which left any previously synced files sitting in the destination directory indefinitely.

`Sync` now always runs, even when its source is empty, so that a destination it has already synced into is correctly reconciled with the (now empty) source and stale files are deleted, exactly as `Sync` is documented to do.

To guard against the common misconfiguration where `from` accidentally resolves to nothing (for example, a mistyped path) while `into` points at some pre-existing, unrelated directory, `Sync` only performs this cleanup for a destination it has a recorded history of syncing into before. The very first time a `Sync` task runs against a given destination, an empty source is treated as suspicious, and the task does nothing rather than risk deleting content it never put there.

See the [upgrading guide](userguide/upgrading_version_9.html#sync_runs_when_source_is_empty) for details on this behavior change and how to opt back into the old skip behavior.

### Platform and toolchain management
Gradle provides comprehensive support for [Native development](userguide/building_cpp_projects.html) and [JVM languages](userguide/building_java_projects.html), featuring automated [Toolchains](userguide/toolchains.html) for seamless JDK management.

#### Groovydoc supports modern Java sources and Groovy 6 output options

The [`Groovydoc`](dsl/org.gradle.api.tasks.javadoc.Groovydoc.html) task now exposes configuration options that had previously only been available through the Groovy CLI, Ant task, or Maven plugin, closing a long-standing gap in Gradle's Groovydoc support.

**Java source parsing level:** Groovydoc uses JavaParser to read Java sources mixed into Groovy projects. When the parser's assumed language level is older than the sources, modern constructs — switch expressions, sealed classes, records, and similar — fail to parse, and the affected classes are silently omitted from the generated documentation. The new `javaVersion` property forwards the language level to Groovydoc so those sources parse cleanly:

```kotlin
tasks.groovydoc {
    javaVersion = JavaLanguageVersion.of(21)
}
```

This option requires Groovy 4.0.27 or later and is silently ignored on earlier Groovy versions.

**Groovy 6.0.0 documentation options:** For projects using Groovy 6.0.0 or later, several new properties are now available on the `Groovydoc` task to control the generated output:

| Property | Purpose |
| --- | --- |
| `showInternal` | Include members annotated with `groovy.transform.Internal` (GEP-17). |
| `noIndex` | Suppress the alphabetical index page. |
| `noDeprecatedList` | Suppress the deprecated-list page. |
| `noHelp` | Suppress the help page. |
| `syntaxHighlighter` | Select the client-side syntax highlighter (`"prism"` or `"none"`). |
| `theme` | Lock the palette (`"auto"`, `"light"`, or `"dark"`). |
| `preLanguage` | Default language id applied to unclassified `<pre>` code blocks. |
| `additionalStylesheets` | Extra stylesheets copied alongside the default. |

All of these are silently ignored on earlier Groovy versions, so they are safe to configure in builds that may be run against multiple Groovy releases.

All new properties are [incubating](userguide/feature_lifecycle.html#feature_preview).

See the [`Groovydoc`](dsl/org.gradle.api.tasks.javadoc.Groovydoc.html) task in the DSL Reference for the full list of configuration options.

### Core plugin and plugin authoring enhancements
Gradle provides a comprehensive plugin system, including built-in [Core Plugins](userguide/plugin_reference.html) for standard tasks and powerful APIs for creating custom plugins.

### Security and infrastructure
Gradle provides robust [security features and underlying infrastructure](userguide/security.html) to ensure that builds are secure, reproducible, and easy to maintain.

### Tooling and IDE integration
Gradle provides [Tooling APIs](userguide/third_party_integration.html) that facilitate deep integration with modern IDEs and CI/CD pipelines.

### General improvements
Gradle provides various incremental updates and performance optimizations to ensure the continued reliability of the build ecosystem.

#### Improved performance on Windows machines with slow system clocks

Gradle reads the system clock frequently while a build runs, to capture execution traces, progress events, and log messages.
On most machines, querying the time is inexpensive.
However, on some Windows systems, particularly virtualized ones, reading the clock is much slower, and the increased cost can accumulate over the many readings taken during a single build invocation.

Gradle now detects these slow system clocks at startup and switches to a faster source of time for the remainder of the build.
On affected machines we have measured build time improvements of up to 45%.

Builds on machines with a normal system clock are unaffected, and no change to configuration is required.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
========================================================== -->


#### Faster Maven publishing with up-to-date POM generation

The [`GenerateMavenPom`](javadoc/org/gradle/api/publish/maven/tasks/GenerateMavenPom.html) task was previously marked as untracked, so it executed on every build regardless of whether the underlying POM had changed.

The task now declares each part of its source POM as a task input, so it participates in up-to-date checks:

```text
$ ./gradlew generatePomFileForMavenPublication
> Task :generatePomFileForMavenPublication UP-TO-DATE

BUILD SUCCESSFUL
```

When a `withXml` action is registered, task input tracking remains disabled, as `withXml` actions do not yet support snapshotting, so the task continues to run on every build.
To restore up-to-date behavior, move the customization into the DSL properties on [`MavenPom`](javadoc/org/gradle/api/publish/maven/MavenPom.html) where possible.

See the [Generate POM task](userguide/publishing_maven.html#publishing_maven:generate-pom) section in the Gradle User Manual for more details.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
========================================================== -->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the "[Feature Lifecycle](userguide/feature_lifecycle.html)" for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Documentation and training

<!--
Add new docs, training, and best practices here
-->

## Fixed issues

<!--
This section will be populated automatically
-->

## Known issues

Known issues are problems that were discovered post-release that are directly related to changes made in this release.

<!--
This section will be populated automatically
-->

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure if you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
