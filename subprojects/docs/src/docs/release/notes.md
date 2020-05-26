The Gradle team is excited to announce Gradle @version@.

This release includes an experimental opt-in for the [file-system watching](#file-watching) feature that significantly improves build times, particularly in incremental scenarios. This is the first in a series of major improvements in the area planned over the course of several upcoming releases.

There are also several other improvements including a [better version ordering](#dependency-ordering), [new samples](#new-samples) and many [bug fixes](#fixed-issues). 

We would like to thank the following community contributors to this release of Gradle:

[SheliakLyr](https://github.com/SheliakLyr),
[Daniil Popov](https://github.com/int02h),
[Scott Robinson](https://github.com/quad),
[Cristian Garcia](https://github.com/CristianGM),
[fransflippo](https://github.com/fransflippo),
[Victor Turansky](https://github.com/turansky),
[Gregor Dschung](https://github.com/chkpnt),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[kerr](https://github.com/hepin1989),
[Chris Doré](https://github.com/oesolutions),
and [Erhard Pointl](https://github.com/epeee).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<a name="performance-improvements"></a>
## Performance improvements

Fast feedback in local incremental builds is crucial for developer productivity. This is especially true if your IDE uses Gradle to build and run your project and run tests, which IntelliJ IDEA does by default. That’s why this scenario is the primary focus of performance improvements in this and several upcoming Gradle releases. 

<a name="file-watching"></a>
### File-system watching

In an [incremental build](userguide/more_about_tasks.html#sec:up_to_date_checks), input and output files are checked to determine what needs to be rebuilt. This feature typically saves a lot of time; however, it adds some I/O overhead, which can be noticeable in large projects when not much has changed since the previous build. 

In this release, we've introduced an experimental _[file-system watching](userguide/gradle_daemon.html#sec:daemon_watch_fs)_ feature. When enabled, it allows Gradle to keep what it has learned about the file-system in memory during and between builds instead of polling the file system on each build. This significantly reduces the amount of disk I/O needed to determine what has changed since the previous build.

You can enable this feature by supplying the parameter `--watch-fs` on the command-line. Eventually, this optimization will be enabled by default.

<a name="features"></a>
## New features and usability improvements

<a name="dependency-ordering"></a>
### Improved dependency version ordering

Gradle uses an implicit [version ordering](userguide/single_versions.html#version_ordering) to determine which version is considered newest when performing dependency version conflict resolution and when deciding which versions are included in a version range.

The current version ordering algorithm can lead to potentially confusing results in certain cases. 

For example, `RC` is considered a higher version than `SNAPSHOT` by Gradle. As a result, a user that intends to integrate with the latest version of a dependency will get the `RC` version through version conflict resolution instead of the `SNAPSHOT` once the `RC` version is released.  

Additionally, versions like `2.0-RC1` or `2.0-SNAPSHOT` are included in a version range with an exclusive bound ending with `2.0[` because they are considered to be lower than `2.0`.

With this version, Gradle provides an opt-in feature that changes version ordering to address these issues. For example, `SNAPSHOT` is considered to be a higher version than `RC` and versions like `2.0-RC1` or `2.0-SNAPSHOT` are excluded from a range ending with `2.0[`. 

Activating the feature preview `VERSION_ORDERING_V2` in `settings.gradle(.kts)` enables these changes:
```
enableFeaturePreview("VERSION_ORDERING_V2")
```

See the [full documentation on version ordering](userguide/single_versions.html) for the details of the new algorithm.

This ordering will be enabled by default in Gradle 7.0.  

## Documentation improvements

<a name="new-samples"></a>
### New samples

The [sample index](samples/) includes new samples covering the following use cases:
- [How to safely use credentials in a Gradle build](samples/sample_credentials_for_external_tool_via_stdin.html)
- [How to develop local changes to two independent projects with a composite build](samples/sample_composite_builds_declared_substitutions.html)
- [How to develop a custom Gradle plugin and test it with a real build](samples/sample_composite_builds_plugin_development.html)

## Improvements for plugin authors

<a name="lazy-dependencies"></a>
### Derive dependencies from user configuration

Gradle now supports using a [`org.gradle.api.provider.Provider`](javadoc/org/gradle/api/provider/Provider.html) when adding dependencies. 

This is useful for plugin authors that need to supply different dependencies based on user-provided configuration.

For example:
```groovy
dependencies {
    // Version of Guava defaults to 28.0-jre but can be changed 
    // via Gradle property (-PguavaVersion=...)
    def guavaVersion = providers.gradleProperty("guavaVersion")
        .orElse("28.0-jre")

    api(guavaVersion.map { "com.google.guava:guava:" + it })
}
```

## Improvements for tooling providers

IDEs can now use a new method from [`GradleConnector`](javadoc/org/gradle/tooling/GradleConnector.html) to gracefully and asynchronously shut down Gradle daemons so that they don't continue to use memory and other resources. This new method is expected to be used when the user exits the IDE.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).

