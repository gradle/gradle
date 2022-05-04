The Gradle team is excited to announce Gradle @version@.

This release includes [building code and running Gradle with Java 18](#java18), [building code with Groovy 4](#groovy4), [much more responsive continuous builds](#continuous-build), [improved diagnostics for dependency resolution](#dependency-diagnostics), as well as [configuration cache improvements](#configuration-cache-improvements) for better performance, [Adoptium toolchain provisioning](#adoptium-provisioning) for JVM, and more.

We would like to thank the following community members for their contributions to this release of Gradle:
[Josh Kasten](https://github.com/jkasten2),
[Marcono1234](https://github.com/Marcono1234),
[mataha](https://github.com/mataha),
[Lieven Vaneeckhaute](https://github.com/denshade),
[kiwi-oss](https://github.com/kiwi-oss),
[Stefan Neuhaus](https://github.com/stefanneuhaus),
[George Thomas](https://github.com/smoothreggae),
[Anja Papatola](https://github.com/apalopta),
[Björn Kautler](https://github.com/Vampire),
[David Burström](https://github.com/davidburstrom),
[Vladimir Sitnikov](https://github.com/vlsi),
[Roland Weisleder](https://github.com/rweisleder),
[Konstantin Gribov](https://github.com/grossws),
[David Op de Beeck](https://github.com/DavidOpDeBeeck),
[aSemy](https://github.com/aSemy),
[Rene Groeschke](https://github.com/breskeby),
[Jonathan Leitschuh](https://github.com/JLLeitschuh),
[Jamie Tanna](https://github.com/jamietanna),
[Xin Wang](https://github.com/scaventz),
[Atsuto Yamashita](https://github.com/att55),
[Taeik Lim](https://github.com/acktsap),
[David Op de Beeck](https://github.com/DavidOpDeBeeck),
[Peter Gafert](https://github.com/codecholeric),
[Alex Landau](https://github.com/AlexLandau),
[Jerry Wiltse](https://github.com/solvingj),
[Tyler Burke](https://github.com/T-A-B),
[Matthew Haughton](https://github.com/3flex),
[Filip Daca](https://github.com/filip-daca),
[Simão Gomes Viana](https://github.com/xdevs23),
[Vaidotas Valuckas](https://github.com/rieske),
[Edgars Jasmans](https://github.com/yasmans)

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<a name="java18"></a>
### Support for Java 18

Gradle now supports running on and building with [Java 18](https://openjdk.java.net/projects/jdk/18/).

<a name="groovy4"></a>
### Support for Groovy 4

Gradle now supports building software using Groovy 4.0. Note that Groovy DSL buildscripts still use Groovy 3.

<a name="continuous-build"></a>
### Continuous Build is much more responsive on Windows and macOS with Java 9+

[Continuous Build](userguide/command_line_interface.html#sec:continuous_build) automatically re-executes the build with the same requested tasks when inputs change.
This allows for continuous feedback during development.

Because of the internal changes in the JDK, Continuous Build did not work well on Windows and macOS on Java 9 and higher.
It could take up to 10 seconds to detect a change and trigger a build.

Now Gradle picks up changes nearly instantly on Windows and macOS for all Java versions as well, making Continuous Build respond quickly on all major operating systems. This is because Gradle now uses its own robust and natively implemented file system watching system instead of relying on the generic API in the JDK.

<a name="dependency-diagnostics"></a>
### Improved diagnostic reports for dependency resolution

#### Outgoing Variants

The `outgoingVariants` report now provides additional information that allows further insight into [variant aware dependency resolution](userguide/variant_model.html#sec:variant-aware-matching) results.

This report is useful when determining why a particular variant of this producer project was selected by another consumer project when the producer depends upon the consumer.  Run the report from the producer project, to list every variant built by it (including secondary variants only visible to other local projects).  The output contains the capabilities and attributes present on each variant of the producer, along with other information detailed below.  This output can be compared against the output of the new [`resolvableConfigurations` report](#resolvable-configurations) run in the consumer.
1. Descriptions are now printed for secondary variants, if present.
2. Configurations using `@Incubating` attributes are marked with an `(i)`.
3. The legend at the bottom of the report clarifies the usage of secondary variants.
4. The formatting (and coloring for rich console) of the output is now clearer and more consistent with other reports.
5. Capabilities, Attributes and Artifact lists are alphabetically sorted.
6. Classifiers are now printed next to artifacts if present.
7. New messages when using `--all` and `--variant` options to better describe results (or the lack thereof)

See the [OutgoingVariantsReport](dsl/org.gradle.api.tasks.diagnostics.OutgoingVariantsReport.html) DSL reference for more details.

#### Resolvable Configurations

There is a new `resolvableConfigurations` report available which will display information about all the configurations in a project that can be resolved.

This report compliments the [`outgoingVariants` report](#outgoing-variants) and is meant to be run from the consumer side of a dependency to determine why a particular variant of a producer project was selected by this consumer project when the consumer depends upon the producer.  It includes the following information:

- Description, Attributes and (directly) extended Configurations
- A `--recursive` option flag can be set to display all configurations which are extended transitively
- Attributes affected by Compatibility or Disambiguation rules during resolution listed
- A `--configuration` option can limit this report to a single configuration
- A `--all` option flag can be set to include legacy configurations which are both resolvable and consumable; these will be hidden by default
-
See the [ResolvableConfigurations](dsl/org.gradle.api.tasks.diagnostics.ResolvableConfigurations.html) DSL reference for more details.

#### Dependency Insights

The `dependencyInsight` report provides information about a specific dependency, including what variant was selected, and the attributes used during resolution.

The report now uses a table to display variants, which makes it easier to tell where attribute values are from, and see why a particular variant was selected:
1. The variant name is listed at the top, after the word "Variant"
2. The variant output is now in table format.
3. Attributes only present in the variant's metadata only contain text in the "Provided" column
4. Attributes present in both the variant's metadata and requested by the configuration contain text in both columns
5. Attributes only requested by the configuration only contain text in the "Requested" column
6. The table is sorted first by groups (3), (4), and (5); then alphabetically inside each group.

<a name="dependency-resolution-results-task-inputs"></a>
### Dependency resolution results can be used as task inputs

Tasks may need to access dependency resolution results.
For example, built-in tasks like `dependencies` and `dependencyInsight` do so in order to provide reporting about resolved artifacts and dependency graphs.
Other tasks may produce file outputs based on dependency resolution results.
Previously, it was only possible by performing dependency resolution in a task action.
However, this resulted in suboptimal performance.

Starting with Gradle 7.5 it is now possible to declare dependency resolution results as task inputs.

This allows writing tasks which consume dependency resolution results.
Declaring such inputs instead of doing undeclared dependency resolution in task actions allows Gradle to optimise for build incrementality.
Additionally, these new types of task inputs are fully supported by the [configuration cache](userguide/configuration_cache.html).

You can learn more in the [Authoring Tasks](userguide/more_about_tasks.html#sec:task_input_using_dependency_resolution_results) user manual chapter and with the dedicated [sample](samples/sample_tasks_with_dependency_resolution_result_inputs.html).

<a name="configuration-cache-improvements"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing this for subsequent builds.

#### Running external processes at configuration time

Previously, external processes started with `exec` or `javaexec` APIs were ignored by configuration cache, and it could be a potential correctness issue if the output of the external process affects the configuration.

A [new Provider-based API](javadoc/org/gradle/api/provider/ProviderFactory.html#exec-org.gradle.api.Action-) is
now available to obtain the output of the external process in the configuration-cache-compatible way. The `exec` and `javaexec` APIs are now [disallowed](userguide/configuration_cache.html#config_cache:requirements:external_processes) if the configuration cache is enabled in order to prevent potential issues.

If a more complex interaction with the external process is necessary, then a custom [`ValueSource`](javadoc/org/gradle/api/provider/ValueSource.html) implementation
can be used. The injected [`ExecOperations`](javadoc/org/gradle/process/ExecOperations.html) service should be used to run the external process.

#### Files read at configuration time become build configuration inputs

Files read at configuration time with `FileInputStream` or some Kotlin APIs now automatically become build configuration inputs. The configuration cache is invalidated if the contents of such file(s) change between builds.

Previously, file reads were ignored, and it could be a potential correctness issue if the contents of the file(s) affected the configuration.

#### New ways to access environment without unnecessary invalidations of the configuration cache

Since the automatic build configuration inputs detection was introduced in Gradle 7.4, some common patterns of reading subsets of environment variables or system properties were causing excessive invalidations of the configuration cache, leading to suboptimal performance. For example, iterating over all environment variables to find the ones with names starting with some prefix caused all available variables, even the unrelated ones, to become configuration inputs.

Two new options are now available to mitigate that. For simpler use cases, there are the Provider-based APIs to access [system properties](javadoc/org/gradle/api/provider/ProviderFactory.html#systemPropertiesPrefixedBy-java.lang.String-) or [environment variables](javadoc//org/gradle/api/provider/ProviderFactory.html#environmentVariablesPrefixedBy-java.lang.String-) with names starting with some prefix. Advanced processing, like filtering names with regular expression, can be done inside a custom [`ValueSource`](javadoc/org/gradle/api/provider/ValueSource.html) implementation. Reading a file, an environment variable, or a system property no longer adds a build configuration input inside the implementation of the `ValueSource`. The value of the `ValueSource` is recomputed each time the build runs, and the configuration cache entry is only invalidated if the value changes.

#### New compatible plugins and tasks

The [`kotlin-dsl`](userguide/kotlin_dsl.html#sec:kotlin-dsl_plugin) plugin is now compatible with the configuration cache.

The `dependencyInsight`, `outgoingVariants` and `resolvableConfigurations` tasks are now compatible with the configuration cache.

### JVM toolchains improvements

[Java toolchains](userguide/toolchains.html) provide an easy way to declare which Java version your project should be built with.
By default, Gradle will [detect installed JDKs](userguide/toolchains.html#sec:auto_detection) or automatically download new toolchain versions.

<a name="adoptium-provisioning"></a>
#### Java toolchains can download any JDK by automatically selecting between Adoptium and AdoptOpenJDK

Gradle now checks the Adoptium API first when downloading JDKs, rather than only using the legacy AdoptOpenJDK API. This allows downloading the new JDK 18 releases, which are not available via AdoptOpenJDK, while still maintaining the ability to download versions that are no longer supported by Adoptium, such as JDK 9-10 and 12-16.

There is a new Gradle property `org.gradle.jvm.toolchain.install.adoptium.baseUri` to control the Adoptium base URI. This is in addition to the`org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri` property, which is still used if a JDK is not found in the Adoptium API.

### Description available on secondary variants

When defining [secondary variants](userguide/publishing_customization.html#sec:publishing-custom-components), which are variants available only to other local Gradle projects, there is a new [ConfigurationVariant](javadoc/org/gradle/api/artifacts/ConfigurationVariant.html#getDescription--) method available to supply a note or description for the variant.
These descriptions will be printed by the `outgoingVariants` report and defaults have been added for existing secondary variants produced by the Java plugin.

### Improved test sources separation in the `eclipse` plugin

The `eclipse` plugin has improved support for [test sources](https://www.eclipse.org/eclipse/news/4.8/jdt.php#jdt-test-sources).
The Eclipse classpath file generated by the plugin has the following changes:

- Project dependencies defined in test configurations get the `test=true` classpath attribute
- All source sets and dependencies defined by the JVM Test Suite plugin are also marked as test code by default
- The `eclipse` plugin DSL exposes properties to configure test sources

```
eclipse {
    classpath {
        testSourceSets = [sourcesSets.test, sourceSets.myTestSourceSet]
        testConfigurations = [configuration.myTestConfiguration]
    }
}
```

Note, that these changes affect the [Buildship](https://eclipse.org/buildship) plugin's project synchronization as well.

See [the documentation](userguide/eclipse_plugin.html#sec:test-sources) for more details.

### Query a single property with the `properties` task

The built-in `properties` task prints all project properties to the console. Now, the task takes an optional `--property` argument which configures it to display the selected property only.
```
$ gradle properties --property buildFile

> Task :properties

------------------------------------------------------------
Root project 'example-project'
------------------------------------------------------------

buildFile: /path/to/project/build.gradle

BUILD SUCCESSFUL in 550ms
1 actionable task: 1 executed
```

This is useful for keeping track of specific properties on CI systems, and requires much less parsing and filtering than before.

### Groovydoc exposes more options

The [`Groovydoc`](dsl/org.gradle.api.tasks.javadoc.Groovydoc.html) task now exposes more options:

- `access`: for controlling the access levels included in the documentation, defaults to `PROTECTED`
- `includeAuthor`: for controlling whether the author is displayed in the documentation, defaults to `false`
- `processScripts`: for controlling whether scripts are included in the documentation, defaults to `true`
- `includeMainForScripts`: for controlling whether a script's `main` method is included in the documentation, defaults to `true`

These defaults are the same as what was previously used, so there should be no changes to the default behavior.

### --show-version (-V) flag

The `-V` flag (long form `--show-version`) instructs Gradle to first print version information and then continue executing any requested tasks.  This is in contrast to the pre-existing `-v` (long form `--version`) flag which prints version information and then immediately exits.

This flag may be useful in CI environments to record Gradle version information in the log as part of a single Gradle execution.

### Checkstyle tasks use toolchains and execute in parallel by default

The [Checkstyle plugin](userguide/checkstyle_plugin.html) now uses the Gradle worker API to run Checkstyle as an external worker process, so that multiple Checkstyle tasks may now run in parallel within a project. This can greatly increase overall build performance when several of these tasks exist within a single project. The memory used by the process is controlled via the `minHeapSize` and `maxHeapSize` properties.

Checkstyle now uses [JVM toolchains](userguide/toolchains.html) in order to minimize JDK installation requirements. In Java projects, Checkstyle will use the same version of Java required by the project. In other types of projects, Checkstyle will use the version of Java that is used by the Gradle daemon.

### Run a single PMD task on multiple threads

[PMD](https://pmd.github.io/) is a quality analysis tool that runs on the Java source files of your project.

With this version of Gradle, the [`thread` parameter](https://pmd.github.io/latest/pmd_userdocs_tools_ant.html#parameters) PMD offers is now exposed through the PMD extension and tasks.
This allows configuration of PMD to run its analysis on more than one thread.

See the [documentation](userguide/pmd_plugin.html#sec:pmd_conf_threads) for more information.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

- The [TargetJvmEnvironment](javadoc/org/gradle/api/attributes/java/TargetJvmEnvironment.html) interface is now stable.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
