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
[devareddy05](https://github.com/devareddy05).

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

### Isolated Projects
[Isolated Projects](userguide/isolated_projects.html) is an incubating performance feature that safely runs project configuration in parallel, significantly reducing configuration time in many scenarios, including IDE sync and CI builds.

### Configuration Cache improvements
Gradle provides a [Configuration Cache](userguide/configuration_cache.html) that improves build time by caching the result of the configuration phase and reusing it for subsequent builds.

### Test reporting and execution
Gradle provides a [set of features and abstractions](userguide/java_testing.html) for testing JVM code, along with test reports to display results.

#### Test filters apply to JUnit Platform tests that are not declared as methods

[Test filters](userguide/java_testing.html#test_filtering) now apply to JUnit Platform tests that are not declared as methods, such as [ArchUnit](https://www.archunit.org/) rules annotated with `@ArchTest` on fields or [Spek](https://www.spekframework.org/) scopes.
Previously, such tests were always executed, so requesting a single test with `--tests` or excluding tests with `excludeTestsMatching` had no effect on them.

Gradle matches such tests by their enclosing test class and the name they are reported with, in the same way that test methods are matched by class and method name.
For a rule declared in a field `firstRule` of `ArchRulesTest`, `--tests "ArchRulesTest.firstRule"` runs only that rule and `excludeTestsMatching "*firstRule"` skips it.
Rules grouped in an `ArchTests` set are reported as `RuleSet > rule`, so they are matched by that name, not by the field name alone.
Tests that an engine registers only while executing, as [Kotest](https://kotest.io/) does, are not visible to the filter and can only be selected by their spec class.

This also affects the [Test Retry plugin](https://github.com/gradle/test-retry-gradle-plugin), which uses test filters to select the tests to run again.
Previously, all such tests were retried whenever any test in the task was retried, even if they had passed.
Now only the failed ones are retried.

See the [Test filtering](userguide/java_testing.html#filtering_non_method_tests) section in the Gradle User Manual for more details.

### CLI, logging, and problem reporting
Gradle provides an intuitive [command-line interface](userguide/command_line_interface.html), detailed [logs](userguide/logging.html), and a structured [problems report](userguide/reporting_problems.html#sec:generated_html_report) that helps developers quickly identify and resolve build issues.

#### Taskbar progress in Windows Terminal

The build progress shown in the terminal is now also reported to the Windows taskbar when running in [Windows Terminal](https://learn.microsoft.com/en-us/windows/terminal/) 1.6 or later, in addition to ConEmu, Ghostty, kitty and iTerm2.
This makes it easy to see how far a long-running build has progressed while the terminal window is in the background, and it also works in WSL shells hosted by Windows Terminal.

See the [Rich console](userguide/command_line_interface.html#sec:rich_console) section in the Gradle User Manual for more details.

#### Predefined problem groups in the Problems API

The incubating [Problems API](userguide/reporting_problems.html#header) now provides a predefined hierarchy of problem groups, available from the `Problems` service as `problems.groups`.
Plugins report into the matching predefined group or add a subgroup below one.
The types of the hierarchy enforce placement:

```kotlin
problems.reporter.report(problems.groups.compilation.java.problemId("Unused import")) {}
problems.reporter.report(problems.groups.transformation.group("KMP").problemId("Bundle failed")) {}
```

Gradle's deprecation warnings are reported into `Gradle > Deprecation`, named after the kind of deprecation, for example `Task usage` instead of `deprecation:the-buildneeded-task-has-been-deprecated`. Details of the usage, such as the name of a task or configuration, no longer appear in the name; they remain in the problem's contextual label.

Predefined groups are documented with a description of what belongs in them, giving consumers of problem reports a documented set of group names to navigate, filter, and aggregate the problems plugins report.
On the console, problems now show the chain of groups they belong to, for example `Unused import (in Compilation > Java)`.
The existing `ProblemGroup.create()` and `ProblemId.create()` methods keep working; migrating to the predefined groups is recommended.

Gradle's own compilation problems now use the predefined groups: Java compiler diagnostics and compiler initialization failures report into `Compilation > Java`, a missing `tools.jar` for the Groovy compiler into `Compilation > Groovy`, and Groovy DSL script compilation failures into `Gradle > DSL Evaluation`.
Both the group and the name of these problem ids changed, so Tooling API and report consumers that match on the old ids need to update.
For example, `compilation:java:initialization-failed` is now `Compilation:Java:Compiler initialization failed`.
The console heading for a failed compiler start changes with it, from `Java compilation initialization error` to `Compiler initialization failed`.
Java compiler diagnostics are named after the kind of diagnostic, for example `Cannot find symbol` instead of the javac code `compiler.err.cant.resolve.location`; the compiler's message stays in the problem's contextual label.

Gradle's own dependency problems use the predefined groups as well: version catalog problems report into `Dependencies > Declaration`, failures while resolving the dependency graph, such as selecting a version, a variant, or a capability, into `Dependencies > Graph Resolution`, and artifact selection failures into `Dependencies > Artifact Resolution`.
They are now named by their former display names, and version catalog problem names mention the version catalog, for example `dependency-version-catalog:toml-syntax-error` is now `Dependencies:Declaration:Version catalog TOML syntax error`.
See the [upgrade guide](userguide/upgrading_version_9.html#dependency_problem_ids_moved_to_predefined_groups) for the complete list.

Gradle's own validation problems use the predefined groups as well: problems about input, output, and caching annotations report into `Gradle > Plugin Validation`, problems about how build logic uses the build model, including types that cannot be part of a Declarative schema, into `Gradle > Build Logic`, and missing or unusable property values into `Gradle > Build Definition`.
They are now named by their former display names, with a few names reworded to describe the problem, for example `validation:property-validation:value-not-set` is now `Gradle:Build Definition:Value not set`.
See the [upgrade guide](userguide/upgrading_version_9.html#validation_problem_ids_moved_to_predefined_groups) for the complete list.

See the [Predefined Problem Groups](userguide/reporting_problems.html#sec:predefined_problem_groups) section in the Gradle User Manual for more details.

### Build authoring improvements
Gradle provides [rich APIs](userguide/getting_started_dev.html) for build engineers and plugin authors, enabling the creation of custom, reusable build logic and better maintainability.

#### The `wrapper` task preserves customized properties

The [`wrapper`](userguide/gradle_wrapper.html#gradle_wrapper) task now preserves values customized in an existing `gradle-wrapper.properties` file when they are not explicitly configured on the task. Preserved values include the network timeout, URL validation, retries, retry backoff, and the distribution and archive paths and bases. Explicit task configuration still takes precedence.

For example, `validateDistributionUrl=false` (previously set by running `./gradlew :wrapper --no-validate-url`) now persists when the wrapper is regenerated.

User-declared `Wrapper` tasks now also write the default network timeout, retry count, and retry backoff when those properties are not otherwise configured.

See the [Preserving Existing Wrapper Properties](userguide/gradle_wrapper.html#sec:preserving_wrapper_properties) section in the Gradle User Manual for more details.

#### Providers for values that are already known

Build logic often has a value at hand and needs to pass it to an API that accepts a [`Provider`](javadoc/org/gradle/api/provider/Provider.html).
Previously, this required wrapping the value in a `Callable` with `providers.provider { value }`, or `providers.provider { null }` for a provider that has no value.

[`ProviderFactory`](javadoc/org/gradle/api/provider/ProviderFactory.html) now has three incubating methods for these cases:

* [`absent()`](javadoc/org/gradle/api/provider/ProviderFactory.html#absent()) returns a provider that never has a value.
* [`present(value)`](javadoc/org/gradle/api/provider/ProviderFactory.html#present(T)) returns a provider that always has the given value.
* [`presentIfNotNull(value)`](javadoc/org/gradle/api/provider/ProviderFactory.html#presentIfNotNull(T)) returns a provider that has the given value when it is not `null`, and has no value otherwise.

```kotlin
val missing: Provider<String> = providers.absent()
val name: PresentProvider<String> = providers.present("my-lib")
val value: Provider<String> = providers.presentIfNotNull(nullableValue)
```

Unlike `provider(Callable)`, these methods do not compute the value on demand, so prefer them when the value is already known.

`present(value)` returns the new [`PresentProvider`](javadoc/org/gradle/api/provider/PresentProvider.html) type, a `Provider` that is guaranteed to have a value: its `get()` never fails and its `getOrNull()` never returns `null`.

See the [Lazy Objects API Reference](userguide/lazy_configuration.html#lazy_objects_api_reference) section in the Gradle User Manual for more details.

### Dependency management enhancements
Gradle provides a flexible [dependency management](userguide/getting_started_dep_man.html) engine for declaring, resolving, and verifying the dependencies your build needs.

### Platform and toolchain management
Gradle provides comprehensive support for [JVM languages](userguide/building_java_projects.html), featuring automated [Toolchains](userguide/toolchains.html) for seamless JDK management.

### Core plugin and plugin authoring enhancements
Gradle provides a comprehensive plugin system, including built-in [Core Plugins](userguide/plugin_reference.html) for standard tasks and powerful APIs for creating custom plugins.

#### `Sync` can empty its destination when its source is empty

A [`Sync`](dsl/org.gradle.api.tasks.Sync.html) task whose source contains no files and no directories, by default, does not run, so its destination directory is not synchronized. What is left in the destination then depends on whether it is a build-owned directory: if it is, the destination is cleaned up; otherwise it keeps the files the source no longer contains.

Setting the new `skipWhenSourceIsEmpty` property to `false` makes the task run in that case as well, so that an empty source empties the destination directory:

```kotlin
tasks.named<Sync>("mySync") {
    skipWhenSourceIsEmpty = false
}
```

`Sync` always deletes the entire contents of its destination directory, not only the files it previously copied there. With `skipWhenSourceIsEmpty` disabled, that also happens when the source is empty - including when it is empty by mistake - so disable it only where nothing other than the task writes to the destination, and use `preserve { ... }` to retain anything the task does not manage.

See [Synchronizing from an empty source](userguide/working_with_files.html#sec:sync_task_empty_source) in the user manual for more details.

### Security and infrastructure
Gradle provides robust [security features and underlying infrastructure](userguide/security.html) to ensure that builds are secure, reproducible, and easy to maintain.

### Tooling and IDE integration
Gradle provides [Tooling APIs](userguide/third_party_integration.html) that facilitate deep integration with modern IDEs and CI/CD pipelines.

### Performance improvements
Gradle continuously improves [build performance](userguide/performance.html) through caching, parallelism, and reduced overhead across all phases of the build.

### General improvements
Gradle provides various incremental updates and performance optimizations to ensure the continued reliability of the build ecosystem.

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

### Custom `dependencies` block API

The remaining incubating parts of the API for [custom `dependencies` blocks](userguide/implementing_gradle_plugins_binary.html#custom_dependencies_blocks) have been promoted:

* [`add(ProviderConvertible)`](javadoc/org/gradle/api/artifacts/dsl/DependencyCollector.html#add(org.gradle.api.provider.ProviderConvertible)) and the `bundle(...)` methods in `DependencyCollector`
* [`constraint(Provider)`](javadoc/org/gradle/api/artifacts/dsl/Dependencies.html#constraint(org.gradle.api.provider.Provider)) and [`constraint(ProviderConvertible)`](javadoc/org/gradle/api/artifacts/dsl/Dependencies.html#constraint(org.gradle.api.provider.ProviderConvertible)) in `Dependencies`
* [`modify(ProviderConvertible)`](javadoc/org/gradle/api/artifacts/dsl/DependencyModifier.html#modify(org.gradle.api.provider.ProviderConvertible)) in `DependencyModifier`
* [`DependencyConstraintFactory`](javadoc/org/gradle/api/artifacts/dsl/DependencyConstraintFactory.html)
* [`PlatformDependencyModifiers`](javadoc/org/gradle/api/plugins/jvm/PlatformDependencyModifiers.html) and [`TestFixturesDependencyModifiers`](javadoc/org/gradle/api/plugins/jvm/TestFixturesDependencyModifiers.html)

### `AndSpec.findUnsatisfiedSpec`

[`AndSpec.findUnsatisfiedSpec(T)`](javadoc/org/gradle/api/specs/AndSpec.html#findUnsatisfiedSpec(T)) has been promoted.
Use it to find the first member spec that an object does not satisfy.

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
