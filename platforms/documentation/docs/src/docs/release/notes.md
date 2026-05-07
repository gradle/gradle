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

Gradle @version@ is the first patch release for Gradle 9.5.0. (released [@releaseDate@](https://gradle.org/releases/)).

The following issues were resolved:

* TODO

We recommend upgrading to Gradle @version@.

---

This release improves [diagnostics and reporting](#diagnostics-and-reporting-improvements) with task provenance in errors and reports that helps to quickly locate the source of a failing task, plus clearer logging when the client JVM is incompatible with daemon requirements, which makes it easier to diagnose unexpected daemon behavior.

[Plugin authors](#core-plugin-and-plugin-authoring-enhancements) gain type-safe Kotlin accessors for precompiled Settings convention plugins that provide IDE autocompletion and compile-time checking, automatic retry support for Wrapper downloads, and the ability to lock Domain Object Collections so that plugins can protect their configured elements from being modified by other plugins.

[Build authoring](#build-authoring-improvements) adds a new environment variable to specify the network address used for client-daemon communication in environments with restrictive network configurations.
Other [improvements](#general-improvements) include an additional `gradle init` option that specifies a target directory, easier Develocity integration, and improved `--help` output.
Finally, the [Tooling API](#tooling-and-ide-integration) now exposes help and version information.

We would like to thank the following community members for their contributions to this release of Gradle:
[atm1020](https://github.com/atm1020),
[mataha](https://github.com/mataha),
[Adam](https://github.com/aSemy),
[Attila Kelemen](https://github.com/kelemen),
[Benedikt Ritter](https://github.com/britter),
[Björn Kautler](https://github.com/Vampire),
[Caro Silva Rode](https://github.com/budindepunk),
[CHANHAN](https://github.com/chanani),
[Dmitry Nezavitin](https://github.com/DmitryNez),
[Eng Zer Jun](https://github.com/Juneezee),
[KugelLibelle](https://github.com/KugelLibelle),
[Madalin Valceleanu](https://github.com/vmadalin),
[Markus Gaisbauer](https://github.com/quijote),
[Oliver Kopp](https://github.com/koppor),
[Philip Wedemann](https://github.com/hfhbd),
[ploober](https://github.com/ploober),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Rohit Anand](https://github.com/R0h1tAnand),
[Suvrat Acharya](https://github.com/Suvrat1629),
[Ujwal Suresh Vanjare](https://github.com/usv240),
[Victor Merkulov](https://github.com/urdak)

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew wrapper --gradle-version=@version@ && ./gradlew wrapper
```

See the [Gradle 9.x upgrade guide](userguide/upgrading_version_9.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

### Diagnostics and reporting improvements

Gradle provides built-in reporting tasks to help you understand and troubleshoot your build.

#### Task provenance in reports and failure messages

When a [task](userguide/more_about_tasks.html) fails, Gradle now includes provenance information in the error message, telling you whether the task was registered by a build script, settings script, or plugin, and which one. This helps you quickly locate the source of a failing task in complex builds with many plugins and subprojects:

```text
Execution failed for task ':app:compileJava' (registered by plugin 'org.gradle.api.plugins.JavaPlugin').
```

Provenance is omitted from failure messages for [verification failures](userguide/custom_tasks.html#verification_failures) (e.g., test failures), since those are expected outcomes rather than configuration issues.

In addition, the outputs of some reports have been enhanced with provenance information.

When running the [help](userguide/command_line_interface.html#sec:show_task_details) task with the  --task argument, task provenance information will be printed:

```bash
> ./gradlew help --task processUrl

Detailed task information for processUrl

Path
     :processUrl (registered in build file 'build.gradle')

Type
     UrlProcess (UrlProcess)
```

When running the [tasks](userguide/command_line_interface.html#sec:listing_tasks) report, there is now a --provenance option available that will display the same information:

```bash
> ./gradlew tasks --provenance

Build tasks
-----------
assemble - Assembles the outputs of this project. (registered by plugin 'org.gradle.language.base.plugins.LifecycleBasePlugin')
build - Assembles and tests this project. (registered by plugin 'org.gradle.language.base.plugins.LifecycleBasePlugin')
```

See [Task Provenance](userguide/more_about_tasks.html#sec:task_provenance) to learn more.

#### Improved diagnostics for daemon JVM compatibility

When Gradle finds an existing [daemon](userguide/gradle_daemon.html) but cannot use it due to JVM incompatibility, it now [logs](userguide/logging.html) the specific reason at the INFO level:

```text
Found daemon DaemonInfo{pid=32935, ...} however its context does not match the desired criteria.
JVM is incompatible.
Wanted: DaemonRequestContext{jvmCriteria=.../corretto-1.8.0_412/... (no Daemon JVM specified, using current Java home), ...}
Actual: DefaultDaemonContext[javaHome=.../jdk-17.0.13+11/..., javaVersion=17, javaVendor=Eclipse Adoptium, ...]
``` 

Previously, Gradle only stated that a new daemon would be used without explaining why the existing one was rejected.

This makes it easier to diagnose unexpected daemon behavior, whether using [--no-daemon](userguide/gradle_daemon.html#sec:disabling_the_daemon), investigating daemon spawns, or troubleshooting JVM compatibility issues.

### Core plugin and plugin authoring enhancements

Gradle provides a comprehensive plugin system, including built-in [Core Plugins](userguide/plugin_reference.html) for standard tasks and powerful APIs for creating custom plugins.

#### Automatic retries for Wrapper downloads

The [Gradle Wrapper](userguide/gradle_wrapper.html) now supports automatic retries when downloading the Gradle distribution.
This helps reduce build failures caused by unstable network connections or temporary server issues.
By default, retries are disabled to preserve existing behavior

To enable retries, add the following properties to `gradle-wrapper.properties`:

```text
retries=3 # Sets the maximum number of retry attempts
retryBackOffMs=1000 # Sets the initial delay between retries (doubles on each failure)
```

See [Configuring Wrapper Retries](userguide/gradle_wrapper.html#sec:configuring_wrapper_retries) to learn more.

#### Type-safe accessors for precompiled Kotlin Settings plugins

Gradle now generates type-safe Kotlin accessors for [precompiled Settings plugins](userguide/pre_compiled_script_plugin_advanced.html) (`*.settings.gradle.kts`), matching the experience already available for project-level precompiled script plugins.

Now, type-safe accessors are generated automatically, giving you IDE autocompletion and compile-time checking:

```kotlin
// build-logic/src/main/kotlin/my-settings-convention.settings.gradle.kts 
plugins {
   id("com.gradle.develocity")
}

// Type-safe accessors, just like in project plugins 
develocity {
   buildScan {
       publishing.onlyIf { false }
   }
}
```

To enable these accessors, ensure your convention plugin build includes the `kotlin-dsl` plugin:

```kotlin
// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}
```

Previously, when writing a precompiled script plugin for Settings, you often had to use string-based APIs to configure extensions or plugins:

```kotlin
// build-logic/src/main/kotlin/my-settings-convention.settings.gradle.kts
plugins {
    id("com.gradle.develocity")
}

// No accessors, string-based API required
extensions.configure<com.gradle.develocity.agent.gradle.DevelocityConfiguration> {
    buildScan {
        publishing.onlyIf { false }
    }
}
```

See the [Gradle Kotlin DSL Primer](userguide/kotlin_dsl.html#kotdsl:accessor_applicability) to learn more.

#### Init Task supports specifying the project directory

The gradle init task now accepts an `--into` option to specify the target directory for the new project.
The directory is created automatically if it doesn't exist:

```bash
gradle init --type java-application --into my-new-project
```

Previously, initializing a project in a new directory required creating it manually and either changing into it or using the global `--project-dir` flag:

```bash
mkdir my-new-project
cd my-new-project
gradle init
```

See [Build Init Plugin](userguide/build_init_plugin.html) to learn more.

#### Domain Object Collections can be made immutable

[Domain Object Collections](userguide/collections.html#available_collections) are the typed containers Gradle uses to manage groups of related build model elements (such as tasks, configurations, source sets, and custom objects contributed by plugins).
Plugin authors can now lock Domain Object Collections to prevent further modifications using the new [disallowChanges() method](javadoc/org/gradle/api/DomainObjectCollection.html#disallowChanges()).
For example, a plugin that populates a collection during configuration can lock it to prevent other plugins from adding unexpected elements or removing existing ones.
This is also useful for preventing modifications after execution has started, when changes would no longer take effect:

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

See the [Javadocs](javadoc/org/gradle/api/DomainObjectCollection.html#disallowChanges()) to learn more.

### Build authoring improvements

Gradle provides [rich APIs](userguide/getting_started_dev.html) for build engineers and plugin authors, enabling the creation of custom, reusable build logic and better maintainability.

#### Explicit bind address for client-daemon and cross-daemon communication

Gradle now supports the `GRADLE_DAEMON_BIND_ADDRESS` environment variable to explicitly specify the network address used for [client-daemon and cross-daemon communication](userguide/gradle_daemon.html).
When set, Gradle skips auto-detection entirely and uses the provided address:

```text
GRADLE_DAEMON_BIND_ADDRESS=192.168.1.10 ./gradlew build
```

Previously, Gradle always attempted to auto-detect the local bind address, selecting the loopback address or falling back to the wildcard address. This could fail in environments with specific network configurations such as multiple network interfaces, multiple TCP/IP stacks, and similar setups.

See [Gradle Environment Variables](userguide/build_environment.html#sec:gradle_environment_variables) to learn more.

### General improvements

Gradle provides various incremental updates and performance optimizations to ensure the continued reliability of the build ecosystem.

#### Easier Develocity integration

Gradle users with access to a [Develocity server](https://gradle.com/develocity/) can now generate a [Build Scan](https://gradle.com/scans/gradle/) without modifying their project configuration.

On the CLI, passing the following option will automatically publish a Build Scan to the passed-in Develocity server:

```bash
./gradlew --develocity-url https://develocity.example.com build
```

See [Build Scan](userguide/build_scans.html#publishing_to_a_specific_develocity_server) for details.

#### Grouped `--help` output

`./gradlew --help` (or `./gradlew -h`) now organizes [CLI options](userguide/command_line_interface.html#sec:command_line_debugging) into logical sections rather than listing them in a single alphabetical block.
The sections are: Built-in (help, version, status, stop), Execution, Configuration, Performance, Security, Diagnostics, Logging, and Develocity.

Previously, all options were listed alphabetically with no grouping, making it hard to find related options or discover what categories of flags were available.
The new layout makes it easier to scan for what you need.

See [CLI Debugging](userguide/command_line_interface.html#sec:command_line_debugging) to learn more.

### Tooling and IDE integration

Gradle provides [Tooling APIs](userguide/third_party_integration.html) that facilitate deep integration with modern IDEs and CI/CD pipelines.

#### Tooling integration improvements

[Tooling API](userguide/tooling_api.html) clients can now access Gradle help and version information directly, the same way the Gradle CLI does.
This allows IDEs and other tools to provide a more consistent user experience when interacting with Gradle.

For example, in IntelliJ IDEA, users can run `--help` and `--version` via the `Execute Gradle task` toolbar action.

## Documentation and training

### Documentation

#### User Manual

The [Isolated Projects](userguide/isolated_projects.html) page has been significantly revised. 
If you are considering adopting this experimental feature, the updated documentation provides a comprehensive overview.

The samples page has been removed.
Code examples can now be found on their corresponding documentation pages, with links to the repository for full project files.
For reference, you can still view the [original samples page](/9.4.1/samples/index.html) from Gradle 9.4.1.

### Training

The following course is now available:
[Dependency Management 1: Configurations](https://dpeuniversity.gradle.com/app/courses/b836790a-444e-4385-b0c2-05f570215167)

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
