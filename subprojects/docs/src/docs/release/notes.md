The Gradle team is excited to announce Gradle @version@.

This release features the [start of several improvements](#incremental-improvements) for faster feedback using [file watching](#file-watching). This release also has a [feature preview](#dependency-ordering) for changes to version ordering and many [bug fixes](#fixed-issues). 

We would like to thank the following community contributors to this release of Gradle:

[Daniil Popov](https://github.com/int02h),
[Scott Robinson](https://github.com/quad),
[Cristian Garcia](https://github.com/CristianGM),
[fransflippo](https://github.com/fransflippo),
[Victor Turansky](https://github.com/turansky),
[Gregor Dschung](https://github.com/chkpnt),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[kerr](https://github.com/hepin1989),
and [Erhard Pointl](https://github.com/epeee).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<a name="incremental-improvements"></a>
## Fast feedback improvements

A large part of the day in the life of a software developer is typically spent making small changes to the code, then rebuilding it, checking the results, and then going back to make other small changes.
We call this the _incremental development use case,_ and ensuring fast feedback for it is a critically important for great developer experience and productivity. 

Starting with the current release we will embark on a mission focused on heavily decreasing the overhead Gradle puts on this use case.
Each forthcoming release in the coming months will include new improvements that we hope will each significantly improve the developer experience.
Taken together we are hoping to see a quantum leap in how developers work with Gradle, especially from inside an IDE.

<a name="file-watching"></a>
### File watching

In this release, we've introduced _[file-system watching](userguide/gradle_daemon.html#sec:daemon_watch_fs)_.
This experimental feature allows Gradle to keep what it has learned about the file-system in memory between builds.
This significantly reduces the amount of disk I/O needed to figure out what has changed since the previous build.

You can enable this feature by supplying the parameter `--watch-fs` on the command-line.

Enabling this feature reduced the time it takes to build small changes to the [Santa Tracker Android application](https://github.com/gradle/santa-tracker-performance):

TBD image for the comparison

<!-- TODO Need to insert link to blog post here -->
Read more about this new feature and its impact [on the Gradle blog](https://blog.gradle.org)!

<a name="dependency-ordering"><a>
## Feature Preview: Dependency version ordering

There are multiple version ordering schemes used in the Java ecosystem.
A number relies on `SNAPSHOT` versions, a concept from Maven.
These versions are special cased in the way they are sorted by Maven, amongst a number of other special suffixes.
With this version, Gradle provides an opt-in feature preview that does the following changes to version sorting:

* Consider `SNAPSHOT` to be a special suffix and sort it after `RC` but before `FINAL` and `RELEASE`
* Consider `GA` to be a special suffix and sort it alphabetically with `FINAL` and `RELEASE`
* Consider `SP` to be a special suffix and sort it higher than `RELEASE`

In addition, this feature preview will also cause version ranges to behave different when the upper bound is an exclusion.
In a range like `[1.2, 2.0[`, versions like `2.0-SNAPSHOT` or `2.0-alpha1` are now excluded.

Activating the feature preview `VERSION_SORTING_V2` in `settings.gradle(.kts)` enables these changes:
```
enableFeaturePreview("VERSION_SORTING_V2")
```

Have a look at the [full documentation on version sorting](userguide/single_versions.html) to understand all the implications.
These changes will become the default in Gradle 7.0.  

<a name="lazy-dependencies"><a>
## Usability: Derive dependencies from user configuration

Gradle now supports using a [`org.gradle.api.provider.Provider`](javadoc/org/gradle/api/provider/Provider.html) when adding dependencies. 

For example:
```groovy
dependencies {
    // Version of Guava defaults to 28.0-jre but can be changed via Gradle property (-PguavaVersion=...)
    def guavaVersion = providers.gradleProperty("guavaVersion").orElse("28.0-jre")

    api(guavaVersion.map { "com.google.guava:guava:" + it })
}
```

This is useful for plugin authors that need to supply different dependencies based upon other configuration that may be set by the user.

## Documentation: New samples available

This release demonstrates a few new use cases as samples:
- How to safely use credentials in a Gradle build
- How to develop local changes to two independent projects with a composite build
- How to develop a custom Gradle plugin and test it with a real build

## Tooling: Improvements for tooling providers

Tooling API clients (like [Eclipse Buildship](https://projects.eclipse.org/projects/tools.buildship) or [IntelliJ IDEA](https://www.jetbrains.com/idea/)) can now use a new method from [`GradleConnector`](javadoc/org/gradle/tooling/GradleConnector.html) to asynchronously cancel all Tooling API connections without waiting for the current build to finish. 

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
