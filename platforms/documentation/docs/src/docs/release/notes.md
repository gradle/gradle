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
[Aharnish Solanki](https://github.com/Ahar28),
[Benedikt Johannes](https://github.com/benediktjohannes),
[Devendra Reddy Pennabadi](https://github.com/devareddy05),
[Dmytro Rodionov](https://github.com/smplio),
[Dreeam](https://github.com/Dreeam-qwq),
[Elías Hernández Rodríguez](https://github.com/EliasHdzR),
[Eng Zer Jun](https://github.com/Juneezee),
[FinlayRJW](https://github.com/FinlayRJW),
[Kamal Kansal](https://github.com/kamalkansal27),
[Marcono1234](https://github.com/Marcono1234),
[Nelson Osacky](https://github.com/runningcode),
[Philip Wedemann](https://github.com/hfhbd),
[Ravi](https://github.com/rkdfx),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Ryan Schmitt](https://github.com/rschmitt),
[Sebastian Schuberth](https://github.com/sschuberth),
[seunghun.ham](https://github.com/seung-hun-h),
[sk-reddy17](https://github.com/sk-reddy17),
[Suvrat Acharya](https://github.com/Suvrat1629),
[Vedant Madane](https://github.com/VedantMadane).

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew :wrapper --gradle-version=@version@ && ./gradlew :wrapper
```

See the [Gradle 9.x upgrade guide](userguide/upgrading_version_9.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

### Isolated Projects improvements

[Isolated Projects](userguide/isolated_projects.html) is an experimental Gradle feature that improves build performance by isolating the mutable state of each project during configuration.
When enabled, projects in a multi-project build are configured in parallel, and configuration results are cached at a finer granularity than the [Configuration Cache](userguide/configuration_cache.html) alone provides.
This can significantly reduce configuration times for large builds, especially during IDE sync in Android Studio and IntelliJ IDEA.

#### Diagnostics mode for migration

When migrating a build to [Isolated Projects](isolated_projects.html#sec:migration), the optimizations that make it fast, parallel project configuration, per-project model caching, and configure-on-demand, can also mask or reorder constraint violations.
This makes it difficult to get a complete picture of all the changes needed in a single build invocation.

Gradle now provides an opt-in _Diagnostics_ mode that disables these optimizations so that every violation is surfaced in a single, deterministic run.
Enable it by setting `org.gradle.unsafe.isolated-projects.diagnostics=true` alongside the Isolated Projects flag:

```text
$ ./gradlew build -Dorg.gradle.unsafe.isolated-projects=true -Dorg.gradle.unsafe.isolated-projects.diagnostics=true
```

Or in `gradle.properties`:

```properties
org.gradle.unsafe.isolated-projects=true
org.gradle.unsafe.isolated-projects.diagnostics=true
```

Diagnostics mode is intended for migration and troubleshooting.
Because parallelism and caching are deliberately disabled, builds in this mode will be slower and should not be committed to version control for regular use.

See the [Diagnostics mode](userguide/isolated_projects.html#sec:diagnostics_mode) section in the Isolated Projects documentation for more details.

### CLI, logging, and problem reporting
Gradle provides an intuitive [command-line interface](userguide/command_line_interface.html), detailed [logs](userguide/logging.html), and a structured [problems report](userguide/reporting_problems.html#sec:generated_html_report) that helps developers quickly identify and resolve build issues.

#### Non-interactive mode

Gradle now supports a `--non-interactive` command-line option to disable all interactive console prompting.
This is useful for running Gradle in automated environments such as CI pipelines, scripts, and AI agents where no user input is available.

See the [Non-interactive mode](userguide/command_line_interface.html#sec:non_interactive) section in the Gradle User Manual for more information.

#### NO_COLOR support

Gradle now honors the `NO_COLOR` environment variable following the [no-color.org](https://no-color.org/) convention.
When `NO_COLOR` is set and non-empty, Gradle suppresses color output while preserving other styling (bold, underline) and rich features (progress bars, animations).

![NO-COLOR Screenshot](release-notes-assets/no-color-screenshot.png)

See the [Environment variables](userguide/build_environment.html#sec:gradle_environment_variables) section in the Gradle User Manual for more information.

### Build authoring improvements
Gradle provides [rich APIs](userguide/getting_started_dev.html) for build engineers and plugin authors, enabling the creation of custom, reusable build logic and better maintainability.

#### Deprecation of implicit property and method lookup in the project hierarchy

In Gradle's [Groovy and Kotlin DSLs](userguide/kotlin_dsl.html), when a child project's build script references a property or method that isn't defined locally, the resolution mechanism walks up the project hierarchy looking for a match.
For example:

```kotlin
// build.gradle.kts (root project)
extra["foo"] = "hello"
```

```kotlin
// child/build.gradle.kts
println(foo) // Resolved through hierarchy — now deprecated
```

This implicit inheritance creates hidden coupling between projects, makes builds harder to reason about (a typo silently resolves to an ancestor's definition instead of failing), and is fundamentally incompatible with [Isolated Projects](userguide/isolated_projects.html).

Starting in Gradle 9.6.0, both implicit references and explicit APIs (`findProperty()`, `property()`, `hasProperty()`) emit a deprecation warning when they resolve through the hierarchy.
This behavior will be removed in Gradle 10.

See the [upgrade guide](userguide/upgrading_version_9.html#deprecated_implicit_project_hierarchy_lookup) for migration paths, including `gradle.properties`, convention plugins, and explicit references.

#### Opt into Gradle 10 behavior by disabling project hierarchy lookup

Gradle 9.6.0 [deprecates implicit lookup of properties and methods through the project hierarchy](userguide/upgrading_version_9.html#deprecated_implicit_project_hierarchy_lookup); this behavior will be removed in Gradle 10.

Once you have addressed all related deprecations, enable the new `NO_IMPLICIT_LOOKUP_IN_PROJECT_HIERARCHY` feature preview to adopt the Gradle 10 behavior early.

This prevents new accidental implicit lookups in the project hierarchy:

```kotlin
// settings.gradle.kts
enableFeaturePreview("NO_IMPLICIT_LOOKUP_IN_PROJECT_HIERARCHY")
```

Under [Isolated Projects](userguide/isolated_projects.html), the implicit hierarchy lookup is already fully disabled, so this preview only affects non-IP builds.

### Core plugin and plugin authoring enhancements
Gradle provides a comprehensive plugin system, including built-in [Core Plugins](userguide/plugin_reference.html) for standard tasks and powerful APIs for creating custom plugins.

#### Improved validation errors for `@Optional` annotation misuse

The [`validatePlugins`](userguide/java_gradle_plugin.html#sec:plugin_validation) task now produces more specific error messages when the `@Optional` annotation is used incorrectly on task properties.

Previously, a property annotated with only `@Optional` and no input or output annotation produced a generic "missing annotation" error that didn't mention `@Optional` at all.
Now, Gradle explains that `@Optional` is a modifier annotation with no effect on its own:

```text
Type 'MyTask' property 'badProperty' is missing an input or output annotation.

Reason: @Optional is a modifier annotation and has no effect without an input or output annotation.

Possible solutions:
  1. Add an input or output annotation.
  2. Replace @Optional with @Internal for ignoring this property.
```

Similarly, combining `@Internal` with `@Optional` now produces a dedicated error explaining that `@Optional` is redundant on properties excluded from up-to-date checks:

```text
Type 'MyTask' property 'badProperty' annotated with @Internal should not be also annotated with @Optional.

Reason: @Internal properties are excluded from up-to-date checks; @Optional is redundant and not allowed here.
```

See [Validating plugins](userguide/java_gradle_plugin.html#sec:plugin_validation) for more information.

#### Groovy DSL type coercions for lazy properties

Gradle's [lazy property](userguide/lazy_configuration.html) types (`Property<T>`, `ListProperty<T>`, `SetProperty<T>`) previously required exact type matches when assigning values in the [Groovy DSL](userguide/groovy_build_script_primer.html).
This meant that common idioms that worked with eager properties would fail with `IllegalArgumentException` when a plugin author migrated to lazy properties.

Gradle now automatically coerces values in the following cases:

**String to File** — A `String` assigned to a `Property<File>`, `RegularFileProperty`, or `DirectoryProperty` is resolved relative to the project directory:

```groovy
task.workingDir = '/tmp/build'
```

**Single value to collection** — A single `T` or `T[]` assigned to a `ListProperty<T>` or `SetProperty<T>` is wrapped into a one-element collection:

```groovy
task.filter.includePatterns = 'Foo'
task.filter.includePatterns = ['Foo', 'Bar'] as String[]
```

These coercions bring the Groovy DSL experience for lazy properties closer to what users expect from eager properties, making it easier for plugin authors to migrate to the [lazy configuration](userguide/lazy_configuration.html) API without breaking their users' build scripts.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the "[Feature Lifecycle](userguide/feature_lifecycle.html)" for more information.

The following are the features that have been promoted in this Gradle release.

* [`getNetworkTimeout()`](javadoc/org/gradle/api/tasks/wrapper/Wrapper.html#getNetworkTimeout()) in `Wrapper`

## Fixed issues

## Known issues

Known issues are problems that were discovered post-release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure if you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
