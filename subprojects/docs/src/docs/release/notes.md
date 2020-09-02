The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:

[Roberto Perez Alcolea](https://github.com/rpalcolea),
[SheliakLyr](https://github.com/SheliakLyr),
[Christian Edward Gruber](https://github.com/cgruber),
[Rene Groeschke](https://github.com/breskeby),
[Louis CAD](https://github.com/LouisCAD),
[Campbell Jones](https://github.com/serebit),
[Leonardo Bezerra Silva Júnior](https://github.com/leonardobsjr),
[Christoph Dreis](https://github.com/dreis2211),
[Matthias Robbers](https://github.com/MatthiasRobbers),
[Vladimir Sitnikov](https://github.com/vlsi),
[Stefan Oehme](https://github.com/oehme),
[Thad House](https://github.com/ThadHouse),
and [Michał Mlak](https://github.com/Miehau).

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

An example of such a dependency is an annotation library with annotations that are not used at runtime.
These typically need to be available at compile time of the library's consumers when annotation processors inspect annotations of all classes.
Another example is a dependency that is part of the runtime environment the library is expected to run on, but also provides types that are used in the public API of the library.

The [Java Library Plugin](userguide/java_library_plugin.html#sec:java_library_configurations_graph) now offers the `compileOnlyApi` configuration for this purpose.
It effectively combines the major properties of the `compileOnly` configuration (dependency will _not_ be on the runtime classpath)
and the `api` (dependencies are visible for consumers at compile time).

```
plugins {
  id("java-library")
  // add Groovy, Kotlin or Scala plugin if desired
}

dependencies {
  compileOnlyApi("com.google.errorprone:error_prone_annotations:2.4.0")
}
```

The behavior of `compileOnlyApi` dependencies is preserved for published libraries when published with [Gradle Module Metadata](userguide/publishing_gradle_module_metadata.html#).

## Support kebab case formatting when launching a Gradle build with abbreviated names

When running Gradle builds, you can abbreviate project and task names. For example, you can execute the `compileTest` task by running `gradle cT`.

Until this release, the name abbreviation only worked for camel case names (e.g. `compileTest`). This format is recommended for task names, but it is unusual for project names. In the Java world the folder names are lower case by convention. 

Many projects - including Gradle - overcome that by using kebab case project directories (e.g. `my-awesome-lib`) and by defining different, camel case project names in the build scripts. This difference leads to unnecessary extra complexity in the build.

This release fixes the issue by adding support for kebab case names in the name matching. Now, you can execute the `compileTest` task in the `my-awesome-lib` subproject with the following command:
```
gradle mAL:cT
```

Note, that even though the kebab case name matching works with tasks too, the recommendation is still to use camel case for them. 

To learn more about name abbreviation, check out the [user guide](userguide/command_line_interface.html#task_name_abbreviation).

## Support for version ranges in repository content filtering

With [repository content filtering](userguide/declaring_repositories.html#sec:repository-content-filtering), builds can control which repositories are queried for which dependency.
This feature provides performance and security benefits.

With this release, when including or excluding a specific dependency version, the build author can use a version range:

```
repositories {
    maven {
        url = 'http://some-url'
        content {
             excludeVersion('com.google.guava', 'guava', '[19.0,)')
       }
    }
}
```

In this case, no `guava` version after `19.0` will be searched for in the referenced Maven repository.

## Gradle init improvements

<-- TBD: add something if we think it is worth mentioning, see #14219 and #14210 -->

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
