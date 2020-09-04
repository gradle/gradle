The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

## Support for JDK15

TODO: Expand this section

## File system watching is ready for production use

In an [incremental build](userguide/more_about_tasks.html#sec:up_to_date_checks), input and output files are checked to determine what needs to be rebuilt.
This feature typically saves a lot of time; however, it adds some I/O overhead, which can be noticeable in large projects when not much has changed since the previous build. 

Back in Gradle 6.5 we've introduced _[file-system watching](userguide/gradle_daemon.html#sec:daemon_watch_fs)_ as an experimental feature.
When enabled, it allows Gradle to keep what it has learned about the file-system in memory during and between builds instead of polling the file system on each build.
This significantly reduces the amount of disk I/O needed to determine what has changed since the previous build.

This feature is now ready for production use and supported on Linux, Windows and macOS.
You can enable it by adding the following to `gradle.properties` in the project root or in your Gradle user home:

```
org.gradle.vfs.watch=true
```

Read more about this new feature and its impact [on the Gradle blog](https://blog.gradle.org/introducing-file-system-watching)!

![Build time improvements using Santa Tracker Android with file-system watching enabled, Linux with OpenJDK 8.](https://blog.gradle.org/images/introducing-file-system-watching/watch-fs-santa-tracker-linux.png)

_Build time improvements using [Santa Tracker Android](https://github.com/gradle/santa-tracker-performance) with file-system watching enabled, Linux with OpenJDK 8._

## Configuration cache improvements

TBD - load from cache performance improvements and reduced memory consumption for Android builds

## Compile-only API dependencies can be declared for JVM libraries

When writing a Java (or Groovy/Kotlin/Scala) library, there are cases where you require dependencies at compilation time which are parts of the API of your library, but which should not be on the runtime classpath.

<!-- 
Add release features here!
## 1

details of 1

## 2

details of 2

## n
-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
