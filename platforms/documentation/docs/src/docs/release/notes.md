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
[/dev/mataha](https://github.com/mataha),
[Adam](https://github.com/aSemy),
[Attila Kelemen](https://github.com/kelemen),
[Benedikt Ritter](https://github.com/britter),
[Björn Kautler](https://github.com/Vampire),
[Caro Silva Rode](https://github.com/budindepunk),
[CHANHAN](https://github.com/chanani),
[Eng Zer Jun](https://github.com/Juneezee),
[Madalin Valceleanu](https://github.com/vmadalin),
[Markus Gaisbauer](https://github.com/quijote),
[Philip Wedemann](https://github.com/hfhbd),
[ploober](https://github.com/ploober),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Rohit Anand](https://github.com/R0h1tAnand),
[Suvrat Acharya](https://github.com/Suvrat1629),
[Ujwal Suresh Vanjare](https://github.com/usv240),
[Victor Merkulov](https://github.com/urdak).

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew wrapper --gradle-version=@version@ && ./gradlew wrapper
```

See the [Gradle 9.x upgrade guide](userguide/upgrading_version_9.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

### Core plugin and plugin authoring enhancements

Gradle provides a comprehensive plugin system, including built-in [Core Plugins](userguide/plugin_reference.html) for standard tasks and powerful APIs for creating custom plugins.

#### Type-safe accessors for precompiled Kotlin Settings plugins

Gradle now generates type-safe Kotlin accessors for [precompiled convention Settings plugins](userguide/pre_compiled_script_plugin_advanced.html) (`*.settings.gradle.kts`).

Previously, when writing a convention plugin for `settings.gradle.kts`, you often had to use string-based APIs to configure extensions or plugins.
Now, as long as the `kotlin-dsl` plugin is applied, Gradle generates accessors that provide IDE autocompletion and compile-time checking for your settings scripts, matching the experience already available for Project-level convention plugins.

To enable these accessors, ensure your convention plugin build includes the `kotlin-dsl` plugin:

```kotlin
// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}
```

### Build authoring improvements

Gradle provides [rich APIs](userguide/getting_started_dev.html) for build engineers and plugin authors, enabling the creation of custom, reusable build logic and better maintainability.

#### Domain Object Collections can be made immutable

Plugin and build authors can now lock domain object collections to prevent further modifications using the new `disallowChanges()` method:

- Once `disallowChanges()` is called, elements can no longer be added to or removed from the collection.
- Invoking this method does not force the realization of lazy items previously added to the collection.
- This lock applies only to the collection itself. Individual objects within the collection can still be modified.

```kotlin
val myCollection = objects.domainObjectContainer(MyType::class)
val main = MyType("main")

myCollection.add(main)
myCollection.add(MyType("test"))

myCollection.disallowChanges()    // the collection is now immutable
main.setFoo("bar")                // individual elements can still be modified

myCollection.add(MyType("other")) // this will fail
myCollection.remove(main)         // this will fail
```

#### GitHub permalinks in Gradle Wrapper and application start scripts

Gradle Wrapper scripts and application start scripts now include links to the GitHub source templates they were generated from.

Previously, these links always pointed to the latest template versions rather than the version used to generate the script.
In this release, scripts link to the exact template version they were generated from.

#### Explicit bind address for client-daemon and cross-daemon communication

Gradle now supports the `GRADLE_DAEMON_BIND_ADDRESS` environment variable to explicitly specify the network address used for client-daemon and cross-daemon communication.

Previously, Gradle always attempted to auto-detect the local bind address, which could fail in environments with specific network configurations (multiple network interfaces, multiple TCP/IP stacks, etc.).

Setting `GRADLE_DAEMON_BIND_ADDRESS` to an IP address or hostname will skip auto-detection and use the provided address directly:

```text
GRADLE_DAEMON_BIND_ADDRESS=192.168.1.10 ./gradlew build
```

### Diagnostics and reporting improvements

Gradle provides built-in reporting tasks to help you understand and troubleshoot your build.

#### Task provenance in reports and failure messages

Gradle now displays _provenance_ information for tasks — the plugin or build script that registered each task.
This makes it easier to understand where tasks come from and diagnose failures in complex builds with many plugins.

**Task reports:** The `tasks` report now supports a `--provenance` option that shows where each task was registered:

```text
> ./gradlew tasks --provenance

Build tasks
-----------
assemble - Assembles the outputs of this project. (registered by plugin 'org.gradle.language.base.plugins.LifecycleBasePlugin')
build - Assembles and tests this project. (registered by plugin 'org.gradle.language.base.plugins.LifecycleBasePlugin')
```

**Help task:** When using `help --task`, the output now includes provenance alongside each task path.

**Failure messages:** When a task fails, the error message now indicates where the failing task was registered, helping you quickly identify the responsible plugin or script:

```text
Execution failed for task ':app:compileJava' (registered by plugin 'org.gradle.api.plugins.JavaPlugin').
```

Provenance is omitted from failure messages for verification failures (e.g., test failures), since those are expected outcomes rather than configuration issues.

### Tooling and IDE integration

Gradle provides [Tooling APIs](userguide/third_party_integration.html) that facilitate deep integration with modern IDEs and CI/CD pipelines.

#### Tooling integration improvements

Tooling API clients can now directly access Gradle help and version information the same way as the Gradle CLI.
This allows IDEs and other tools to provide a more consistent user experience when interacting with Gradle.

For example, In IntelliJ IDEA users will be able to run `--help` and `--version` via the `Execute Gradle task` toolbar action.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the "[Feature Lifecycle](userguide/feature_lifecycle.html)" for more information.

The following are the features that have been promoted in this Gradle release.

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
