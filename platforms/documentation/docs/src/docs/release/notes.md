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

[sk-reddy17](https://github.com/sk-reddy17)

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

### Configuration Cache improvements
Gradle provides a [Configuration Cache](userguide/configuration_cache.html) that improves build time by caching the result of the configuration phase and reusing it for subsequent builds.

### Isolated Projects improvements
Gradle provides [Isolated Projects](userguide/isolated_projects.html), an incubating feature that enables parallel project configuration.

#### Isolated Projects is now incubating

Isolated Projects has graduated from experimental to incubating.
It can now be enabled with the stable `org.gradle.isolated-projects` property and the new `--isolated-projects` CLI option, dropping the `.unsafe.` segment from the previous names.

```properties
# gradle.properties
org.gradle.isolated-projects=true
```

The legacy `org.gradle.unsafe.isolated-projects` property names are now deprecated and will be removed in a future release.
They continue to work as aliases for now.
See the [upgrade guide](userguide/upgrading_version_9.html#deprecated_unsafe_isolated_projects_properties) for the full list of renamed properties.

#### Isolated Projects now offers three modes for handling constraint violations

Builds adopting Isolated Projects typically contain [constraint violations](userguide/isolated_projects.html#sec:constraint_violations) that must be fixed over time.
Isolated Projects now offers three modes, each suited to a different stage of that journey:

- **Fail-fast** — the default. Project configuration runs in parallel and the build fails as soon as a violation is detected, guaranteeing reliable build results.
- **[Diagnostics](userguide/isolated_projects.html#sec:diagnostics_mode)** (`org.gradle.isolated-projects.diagnostics`) — project configuration runs sequentially and the build continues past
  violations, reporting all of them deterministically. Use this to discover what needs fixing during migration.
- **[Dangerously ignore problems](userguide/isolated_projects.html#sec:dangerously_ignore_problems)** (`org.gradle.isolated-projects.dangerously-ignore-problems`) — violations are reported but
  do not fail the build, and parallel configuration stays active. Use this to estimate the parallel build or IDE sync speedup before fixing every violation. Build outputs may be incorrect while
  violations are ignored, so never use this mode to produce artifacts.

The opt-in modes can also be combined, for example to complete an IDE sync that concurrency errors would otherwise interrupt:

```properties
# gradle.properties
org.gradle.isolated-projects=true
org.gradle.isolated-projects.diagnostics=true
org.gradle.isolated-projects.dangerously-ignore-problems=true
```

In all modes, the severity of Isolated Projects violations is now independent of the Configuration Cache `--configuration-cache-problems=warn` flag.

### Test reporting and execution
Gradle provides a [set of features and abstractions](userguide/java_testing.html) for testing JVM code, along with test reports to display results.

#### Test framework initialization failures for TestNG, JUnit 4, and JUnit Platform are always logged to the console

Gradle's [test logging](userguide/java_testing.html#sec:test_logging) now surfaces test-framework startup failures from TestNG, JUnit 4, and JUnit Platform even when the default granularity would otherwise hide them.

Previously, when these frameworks failed to initialize (for example, when a TestNG test class threw an exception from its constructor, a JUnit 4 suite could not be started, or a Jupiter `@BeforeAll` lifecycle hook aborted a container) the failure was silently filtered out by the default granularity.
Users would see only `> There were failing tests` and had to read the XML report to find the underlying cause:

```text
> Task :test FAILED

> There were failing tests. See the report at: file:///.../build/reports/tests/test/index.html

FAILURE: Build failed with an exception.
```

These framework-startup failures now bypass the granularity filter and are always written to the console by default:

```text
> Task :test

ExampleTest > initializationError FAILED
    framework-startup org.testng.TestNGException: Cannot instantiate class ExampleTest
        at org.testng.internal.ObjectFactoryImpl.newInstance(...)
        ...
```

The `testLogging.events` predicate still applies, explicitly silencing `FAILED` events is honored.

The new `TestFailureDetails.isFrameworkFailure()` predicate exposes this distinction to Tooling-API and Build-Scan consumers, who may render framework-startup failures differently from ordinary test failures.

See the [Test logging](userguide/java_testing.html#sec:test_logging) section in the Gradle User Manual for more details.

### CLI, logging, and problem reporting
Gradle provides an intuitive [command-line interface](userguide/command_line_interface.html), detailed [logs](userguide/logging.html), and a structured [problems report](userguide/reporting_problems.html#sec:generated_html_report) that helps developers quickly identify and resolve build issues.

### Build authoring improvements
Gradle provides [rich APIs](userguide/getting_started_dev.html) for build engineers and plugin authors, enabling the creation of custom, reusable build logic and better maintainability.

#### Custom timestamps for reproducible archives

Gradle produces [reproducible archives](userguide/working_with_files.html#sec:reproducible_archives) by default, using fixed timestamps for all entries.
However, some environments, such as those following the [SOURCE_DATE_EPOCH](https://reproducible-builds.org/specs/source-date-epoch/) specification, require a meaningful, verifiable timestamp rather than a fixed default.

Archive tasks now support a [`reproducibleFileTimestamp`](userguide/working_with_files.html#sec:reproducible_timestamp) property that lets you set a custom timestamp for every entry in the archive:

```kotlin
import java.time.Instant

tasks.withType<AbstractArchiveTask>().configureEach {
    reproducibleFileTimestamp = providers.environmentVariable("SOURCE_DATE_EPOCH").map {
        Instant.ofEpochSecond(it.toLong()).toEpochMilli()
    }
}
```

See the [Timestamp for files inside archives](userguide/working_with_files.html#sec:reproducible_timestamp) section in the Gradle User Manual for more details.

### Platform and toolchain management
Gradle provides comprehensive support for [Native development](userguide/building_cpp_projects.html) and [JVM languages](userguide/building_java_projects.html), featuring automated [Toolchains](userguide/toolchains.html) for seamless JDK management.

#### New lazy element provider for Domain Object Collections

[`DomainObjectCollection.getElements()`](javadoc/org/gradle/api/DomainObjectCollection.html#getElements()) returns a `Provider<? extends Collection<T>>` and acts as an important bridge between the [Domain Object Collection](userguide/collections.html) and [Provider APIs](userguide/properties_providers.html).
This API is similar to the existing [`FileCollection.getElements()`](javadoc/org/gradle/api/file/FileCollection.html#getElements()) method.

The returned provider carries build dependencies, meaning dependencies carried by providers added via `addLater` and `addAllLater` are reflected in the returned `elements` provider:

```kotlin
val container = objects.domainObjectSet(MyType::class.java)
container.addLater(someProvider)

// Lazily access all elements as a Provider
val allElements: Provider<out Collection<MyType>> = container.elements

tasks.register("process") {
    inputs.property("items", allElements)
    doLast {
        println(allElements.get())
    }
}
```

See the [Collections](userguide/collections.html#collection_types) section in the Gradle User Manual for more details.

### Core plugin and plugin authoring enhancements
Gradle provides a comprehensive plugin system, including built-in [Core Plugins](userguide/plugin_reference.html) for standard tasks and powerful APIs for creating custom plugins.

### Security and infrastructure
Gradle provides robust [security features and underlying infrastructure](userguide/security.html) to ensure that builds are secure, reproducible, and easy to maintain.

#### Dependency verification reports other trusted keys for the same module or group

When [dependency verification](userguide/dependency_verification.html) fails because an artifact was signed with a key that could not be found on any key server, it can be hard to tell whether you are pulling a brand-new dependency for the first time or whether a previously trusted dependency has had its signing key rotated.

Gradle now appends the number of other keys you already trust for the failing artifact to the message, distinguishing keys trusted for the specific `group:module` from keys trusted for the whole `group`:

```
> On artifact foo-1.0.jar (org:foo:1.0) in repository 'maven': Artifact was signed with key '14F53F0824875D73' but it wasn't found in any key server so it couldn't be verified (1 other key is already trusted for module 'org:foo'; 3 other keys are already trusted for group 'org')
```

A non-zero count is a strong signal that the signing key has been rotated rather than that you are trusting the module or group for the first time, making it easier to react appropriately. This note now appears both in the console output and in the generated HTML verification report.

See the [Verifying dependency signatures](userguide/dependency_verification.html#sec:signature-verification) section in the Gradle User Manual for more details.

### Tooling and IDE integration
Gradle provides [Tooling APIs](userguide/third_party_integration.html) that facilitate deep integration with modern IDEs and CI/CD pipelines.

### General improvements
Gradle provides various incremental updates and performance optimizations to ensure the continued reliability of the build ecosystem.

#### Kotlin DSL accessor generation is no longer stored in the build cache

Generating the [type-safe Kotlin DSL accessors](userguide/kotlin_dsl.html#type-safe-accessors) for a project produces Kotlin source files.
For some projects those files can be sizeable, but their generation is fast.

Storing and fetching those files adds its own overhead when the [Build Cache](userguide/build_cache.html#build_cache) is in use.
That overhead alone is comparable to or higher than the cost of just regenerating the accessors.
Gradle therefore no longer stores Kotlin DSL accessor generation in the Build Cache by default.

Builds that use a remote Build Cache will regenerate accessors locally instead of downloading them; in-build deduplication of accessor generation is unaffected.
Kotlin DSL script compilation continues to be cached as before.

See the [Type-safe model accessors](userguide/kotlin_dsl.html#type-safe-accessors) section in the Gradle User Manual for more details.

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
