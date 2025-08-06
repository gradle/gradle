<meta property="og:image" content="https://gradle.org/images/releases/gradle-default.png" />
<meta property="og:type"  content="article" />
<meta property="og:title" content="Gradle @version@ Release Notes" />
<meta property="og:site_name" content="Gradle Release Notes">
<meta property="og:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:site" content="@gradle">
<meta name="twitter:creator" content="@gradle">
<meta name="twitter:title" content="Gradle @version@ Release Notes">
<meta name="twitter:description" content="We are excited to announce Gradle @version@.">
<meta name="twitter:image" content="https://gradle.org/images/releases/gradle-default.png">

We are excited to announce Gradle @version@ (released [@releaseDate@](https://gradle.org/releases/)).

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:
[Eng Zer Jun](https://github.com/Juneezee),
[EunHyunsu](https://github.com/ehs208),
[HeeChul Yang](https://github.com/yangchef1),
[Jendrik Johannes](https://github.com/jjohannes),
[Johnny Lim](https://github.com/izeye),
[Junho Lee](https://github.com/junstory),
[Kirill Gavrilov](https://github.com/gavvvr),
[Matthew Haughton](https://github.com/3flex),
[Na Minhyeok](https://github.com/NaMinhyeok),
[Philip Wedemann](https://github.com/hfhbd),
[Philipp Schneider](https://github.com/p-schneider),
[Pradyumna C](https://github.com/pradyumnac26),
[r-a-sattarov](https://github.com/r-a-sattarov),
[Ryszard Perkowski](https://github.com/usultis),
[Sebastian Schuberth](https://github.com/sschuberth),
[SebastianHeil](https://github.com/SebastianHeil),
[Staffan Al-Kadhimi](https://github.com/stafak),
[winfriedgerlach](https://github.com/winfriedgerlach),
[Xin Wang](https://github.com/scaventz).

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew wrapper --gradle-version=@version@ && ./gradlew wrapper
```

See the [Gradle 9.x upgrade guide](userguide/upgrading_version_9.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<a name="build-authoring"></a>
### Build authoring improvements

Gradle provides [rich APIs](userguide/getting_started_dev.html) for plugin authors and build engineers to develop custom build logic.

<a name="ear-plugin"></a>
#### Ear plugin

It is possible to generate valid deployment descriptors for [Jakarta EE 11](https://jakarta.ee/release/11/) by specifying the corresponding version in the `deploymentDescriptor` instead of having to use a custom descriptor file.

```kotlin
tasks.ear {
    deploymentDescriptor {  // custom entries for application.xml:
        version = "11"
    }
}
```

#### New `AttributeContainer.addAllLater()`

A new method, [`addAllLater`](javadoc/org/gradle/api/attributes/AttributeContainer.html#addAllLater(org.gradle.api.attributes.AttributeContainer)), has been added to the [`AttributeContainer`](javadoc/org/gradle/api/attributes/AttributeContainer.html) API. 
It allows all attributes from one container to be lazily copied into another.

Here’s an example of how it works:

```kotlin
val color = Attribute.of("color", String::class.java)
val shape = Attribute.of("shape", String::class.java)

val foo = configurations.create("foo").attributes
foo.attribute(color, "green")

val bar = configurations.create("bar").attributes
bar.attribute(color, "red")
bar.attribute(shape, "square")
assert(bar.getAttribute(color) == "red")    // `color` is originally red

bar.addAllLater(foo)
assert(bar.getAttribute(color) == "green")  // `color` gets overwritten
assert(bar.getAttribute(shape) == "square") // `shape` does not

foo.attribute(color, "purple")
bar.getAttribute(color) == "purple"         // addAllLater is lazy

bar.attribute(color, "orange")
assert(bar.getAttribute(color) == "orange") // `color` gets overwritten again
assert(bar.getAttribute(shape) == "square") // `shape` remains the same
```

This API is particularly useful for cases where attributes need to be configured in a deferred or conditional way, such as in plugin development or complex dependency resolution logic.

#### New `Gradle.getBuildPath()`

This release introduces a new method on the [`Gradle`](javadoc/org/gradle/api/invocation/Gradle.html) interface called [`getBuildPath()`](javadoc/org/gradle/api/invocation/Gradle.html#getBuildPath()). 
It returns the path of the build relative to the root of the build tree:

* For the root build, it returns `:`.
* For included builds, it returns their path relative to the root build (e.g., `:my-included-build`).

This is equivalent to what [`BuildIdentifier.getBuildPath()`](javadoc/org/gradle/api/initialization/BuildIdentifier.html#getBuildPath--) provides, but it’s now available directly from the `Gradle` instance, making it easier to determine which build a given project belongs to.

For example, to get the build path for a project:

```kotlin
val project: Project = getProjectInstance()
val buildPath: String = project.gradle.buildPath
```

This complements existing APIs like [`Project.path`](javadoc/org/gradle/api/Project.html#getPath()).

#### Type-safe accessors for `compileOnly` plugin dependencies in precompiled Kotlin scripts

Previously, plugins added via a `compileOnly` dependency could not be applied or configured using [precompiled Kotlin script plugins](userguide/implementing_gradle_plugins_precompiled.html).
Precompiled Kotlin script plugins can use [type-safe accessors](userguide/kotlin_dsl.html#type-safe-accessors) for plugins added via `compileOnly` dependencies. 

For example, the `buildSrc/build.gradle.kts` file below declares a `compileOnly` dependency on a third-party plugin:

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly("com.android.tools.build:gradle:x.y.z")
}
```

A precompiled convention plugin in `buildSrc/src/main/kotlin/my-convention-plugin.gradle.kts` can now apply the plugin and use type-safe accessors to configure it:

```kotlin
plugins {
    id("com.android.application")
}

android {
    // Now accessible via type-safe accessor
}
```

This improvement makes it easier to use and configure third-party plugins in custom build logic.

#### Build initialization uses the `kotlin-test` dependency for Kotlin projects

The [`init` task](userguide/build_init_plugin.html) generates Kotlin project builds using the `org.jetbrains.kotlin:kotlin-test` dependency instead of the more specific `kotlin-test-junit5`.
This change allows the test framework variant (e.g., JUnit5, JUnit4, TestNG) to be inferred automatically based on the configured test runner.

For more details, see the [Kotlin Gradle Configuration documentation](https://kotlinlang.org/docs/gradle-configure-project.html#set-dependencies-on-test-libraries) and the [`kotlin-test` API reference](https://kotlinlang.org/api/core/kotlin-test/).

#### Simpler target package configuration for Antlr 4

The [`AntlrTask`](userguide/antlr_plugin.html) class supports a new [`packageName`](javadoc/org/gradle/api/plugins/antlr/AntlrTask.html#getPackageName()) property for setting the target package of generated code when using Antlr 4.
Previously, specifying the `-package` argument also required manually configuring the output directory to match the package structure.

The new `packageName` property simplifies this by automatically setting both the `-package` argument and the correct output directory based on the package.

Setting the `-package` argument directly is now deprecated and will become an error in Gradle 10.0.0.

```kotlin
tasks.named("generateGrammarSource").configure {
    // Set the target package for generated code
    packageName = "com.example.generated"
}
```

This option is only available when using Antlr 4 and will fail if used with earlier versions.

#### Antlr generated sources are automatically tracked

In previous Gradle versions, if the [`Antlr`](userguide/antlr_plugin.html)-generated sources directory was changed, the associated Java source set was not updated automatically. 
This required manual updates to ensure the source set included the new directory.

With this release, the generated sources directory is now automatically tracked. 
When the output directory changes, the Java source set is updated accordingly.
Additionally, a task dependency is created between the source generation task and the source set, so tasks that consume the source set will correctly depend on Antlr code generation.

#### Declare distribution repository in `MavenPublication.distributionManagement{}`

You can explicitly declare the distribution repository in the POM when publishing a [Maven publication](userguide/publishing_maven.html).

For example, to include GitHub Packages as the distribution repository in the generated POM:

```kotlin
plugins {
  id("maven-publish")
}

publications.withType<MavenPublication>().configureEach {
  pom {
    distributionManagement {
      repository {
        id = "github"
        name = "GitHub OWNER Apache Maven Packages"
        url = "https://maven.pkg.github.com/OWNER/REPOSITORY"
      }
    }
  }
}
```

### Error and warning reporting improvements

Gradle provides a rich set of [error and warning messages](userguide/logging.html) to help you understand and resolve problems in your build.

#### Improved error message for version constraint conflicts

Previously, when a [version constraint conflict](userguide/graph_resolution.html#sec:conflict-resolution) occurred, Gradle produced a verbose and hard-to-read error message, especially when transitive dependencies were involved.

It also was formatted in a way that was difficult to comprehend, especially when constraints involved in the conflict were added by transitive dependencies:

```text
> Could not resolve org:foo:3.2.
  Required by:
      root project 'test'
   > Cannot find a version of 'org:foo' that satisfies the version constraints:
        Dependency path: 'root project :' (conf) --> 'org:bar:2.0' (runtime) --> 'org:foo:3.1'
        Constraint path: 'root project :' (conf) --> 'org:platform:1.1' (platform) --> 'org:foo:{strictly 3.1.1; reject 3.1 & 3.2}'
        Constraint path: 'root project :' (conf) --> 'org:foo:3.2'
        Constraint path: 'root project :' (conf) --> 'org:baz:3.0' (runtime) --> 'org:foo:3.3'
        Constraint path: 'root project :' (conf) --> 'org:other:3.0' (runtime) --> 'org:foo:3.3'
```

This release introduces a cleaner, more focused error message that focuses attention on the conflicting versions required by the constraints involved in the conflict:

```text
> Could not resolve org:foo.
  Required by:
      root project 'mec0k'
   > Component is the target of multiple version constraints with conflicting requirements:
     3.1.1 - directly in 'org:platform:1.1' (platform)
     3.2
     3.3 - transitively via 'org:baz:3.0' (runtime) (1 other path to this version)
```

The improved error message makes dependency version conflicts much easier to diagnose by:

* Clearly states that the failure is due to a version constraint conflict for a component, not just an inability to find a suitable version in the configured repositories.
* Lists each conflicting version constraint involved in the resolution failure.
* Identifies where each constraint is declared (e.g., in the project, a direct dependency, a transitive dependency, or via dependency locking) without printing full dependency paths, which are often long and hard to read. Full paths remain available in the dependency insight report.
* Reports how many resolution paths lead to each constraint, but only prints the first one, which is typically enough to understand the issue.
* Omits non-strict dependency declarations, which don’t contribute to the conflict and only add noise.

Additionally, the error message concludes with a suggested `dependencyInsight` command for further investigation, giving you an actionable next step to explore the conflict in detail.

#### Task graph diagnostic

Gradle offers a new way to visualize [task dependencies](userguide/build_lifecycle.html#task_graph) without executing the build.

Enable it with the `--task-graph` option:

```console
./gradlew root r2 --task-graph
```

This will print a tree-style visualization of the task graph for the specified tasks:

```text
Tasks graph for: root r2
+--- :root (org.gradle.api.DefaultTask)
|    \--- :middle (org.gradle.api.DefaultTask)
|         +--- :leaf1 (org.gradle.api.DefaultTask)
|         \--- :leaf2 (org.gradle.api.DefaultTask, disabled)
\--- :root2 (org.gradle.api.DefaultTask)
    +--- :leaf1 (*)
    |--- other build task :included:fromIncluded (org.gradle.api.DefaultTask)
    \--- :leaf4 (org.gradle.api.DefaultTask, finalizer)
         \--- :leaf3 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
```

This feature provides a quick overview of the task graph, helping users understand the dependencies between tasks without running them.

This feature is _incubating_ and may change in future versions.

#### Fixed `--dry-run` behavior in Composite Builds

Gradle correctly respects [`--dry-run`](userguide/command_line_interface.html#sec:command_line_execution_options) in [Composite Builds](userguide/composite_builds.html), ensuring that tasks are not executed during the execution phase of included builds.

Note that tasks from some included builds may still be executed during configuration time, as part of their configuration logic.

This restores expected behavior and makes `--dry-run` safer for previewing task execution plans across composite builds.

#### Updated Project Report

The [Project Report](userguide/project_report_plugin.html) has been updated to show the physical locations of projects in the file system, as well as their logical build paths:

```text
------------------------------------------------------------
Root project 'avoidEmptyProjects-do'
------------------------------------------------------------

Location: /usr/jsmith/projects/avoidEmptyProjects-do
Description: Example project to demonstrate Gradle's project hierarchy and locations

Project hierarchy:

Root project 'avoidEmptyProjects-do'
+--- Project ':app'
\--- Project ':my-web-module'

Project locations:

project ':app' - /app
project ':my-web-module' - /subs/web/my-web-module

To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :app:tasks
```

This helps authors better understand the structure of hierarchical builds that use non-standard project directories.

<a name="cli"></a>
### CLI improvements

The [command-line interface](userguide/command_line_interface.html) is the primary way users interact with Gradle.
This release introduces several enhancements to improve usability and feedback in the terminal.

#### Rich console off-screen line indicator

When there are more ongoing events than the console can display, the `rich` console shows a helpful status line indicating how many lines are not currently visible:

```console
> (2 lines not showing)
```

This makes it easier to understand when output is being truncated due to limited terminal height.

![Console Shows Off Screen Lines](release-notes-assets/off-screen-lines.gif)

#### Plain console with colors

A new value for the `--console` command line option called `colored` is available:

```console
./gradlew [...] --console=colored
```

This mode enables color output for the console while omitting rich features such as progress bars.

![Console Shows Rich Color Output](release-notes-assets/colored-console.gif)

<a name="config-cache"></a>
### Configuration cache improvements

The [Configuration Cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing it for subsequent builds.
This feature can significantly improve build performance.

#### Encryption honors the JVM’s default keystore type

Previously, Gradle always used the `PKCS12` keystore format for its encryption keystore (used by the [Configuration Cache](userguide/configuration_cache.html)), ignoring the JVM’s default setting.

Starting with this release, Gradle now honors the JVM’s default keystore type, as long as it supports storing symmetric keys. 
If the default keystore is a known format that only supports asymmetric keys, Gradle will automatically fall back to `PKCS12`.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the "[Feature Lifecycle](userguide/feature_lifecycle.html)" for more information.

The following are the features that have been promoted in this Gradle release.

* [`getDependencyFactory()`](javadoc/org/gradle/api/Project.html) in `Project`

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
