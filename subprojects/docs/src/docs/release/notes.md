The Gradle team is excited to announce Gradle @version@.

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

## Toolchains for JVM projects

https://github.com/gradle/gradle/pull/14477

## Support for Java 15

Gradle now supports running on [JDK15](https://openjdk.java.net/projects/jdk/15/). 

## Compile-only API dependencies for JVM libraries

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
  compileOnlyApi(
    "com.google.errorprone:error_prone_annotations:2.4.0")
}
```

The behavior of `compileOnlyApi` dependencies is preserved for published libraries when published with [Gradle Module Metadata](userguide/publishing_gradle_module_metadata.html#).

## Abbreviation of kebab-case project names

When running Gradle builds, you can abbreviate project and task names. For example, you can execute the `compileTest` task by running `gradle cT`.

Until this release, the name abbreviation only worked for camel case names (e.g. `compileTest`). This format is recommended for task names, but it is unusual for project names. In the Java world the folder names are lower case by convention. 

Many projects - including Gradle - overcome that by using kebab case project directories (e.g. `my-awesome-lib`) and by defining different, camel case project names in the build scripts. This difference leads to unnecessary extra complexity in the build.

This release fixes the issue by adding support for kebab case names in the name matching. Now, you can execute the `compileTest` task in the `my-awesome-lib` subproject with the following command:
```
gradle mAL:cT
```

Note, that even though the kebab case name matching works with tasks too, the recommendation is still to use camel case for them. 

To learn more about name abbreviation, check out the [user guide](userguide/command_line_interface.html#task_name_abbreviation).

## `gradle init` improvements

The built-in `init` task can be used to quickly create a new Gradle build or convert a Maven build to a Gradle build.
In this release, the projects generated by `gradle init` have been updated to use the recommended build authoring practices.
The task now also offers additional options.
For new projects, one can now generate a multi-project build for JVM languages.
In order to generate a multi-project build, choose `application`, select one of the JVM languages and then choose `application and library projects`:

```
Select type of project to generate:
  1: basic
  2: application
  3: library
  4: Gradle plugin
Enter selection (default: basic) [1..4] 2

Select implementation language:
  1: C++
  2: Groovy
  3: Java
  4: Kotlin
  5: Scala
  6: Swift
Enter selection (default: Java) [1..6] 3

Split functionality across multiple subprojects?:
   1: no - only one application project
   2: yes - application and library projects
Enter selection [1..2] 2
```  

For [Maven-to-Gradle conversion](userguide/migrating_from_maven), this release adds support for Kotlin DSL scripts and uses buildSrc directory for shared logic.

## New samples

The [sample index](samples) includes new samples, with step-by-step instructions on how to get started with Gradle for different project types and programming languages:

- Building a monolithic application:
[Java](samples/sample_building_java_applications.html),
[Groovy](samples/sample_building_groovy_applications.html),
[Scala](samples/sample_building_scala_applications.html),
[Kotlin](samples/sample_building_kotlin_applications.html),
[C++](samples/sample_building_cpp_applications.html),
[Swift](samples/sample_building_swift_applications.html)
- Building an application with libraries:
[Java](samples/sample_building_java_applications_multi_project.html),
[Groovy](samples/sample_building_groovy_applications_multi_project.html),
[Scala](samples/sample_building_scala_applications_multi_project.html),
[Kotlin](samples/sample_building_kotlin_applications_multi_project.html)
- Building an isolated library:
[Java](samples/sample_building_java_libraries.html),
[Groovy](samples/sample_building_groovy_libraries.html),
[Scala](samples/sample_building_scala_libraries.html),
[Kotlin](samples/sample_building_kotlin_libraries.html),
[C++](samples/sample_building_cpp_libraries.html),
[Swift](samples/sample_building_swift_libraries.html)

## Version ranges in repository content filtering

Builds can control which repositories are queried for which dependency for better performance and security using [repository content filtering](userguide/declaring_repositories.html#sec:repository-content-filtering).
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

## Ignore dependencies in dependency lock state

Dependency locking can be used in cases where reproducibility is not the main goal.
As a build author, you may want to have different frequency of dependency version updates, based on their origin for example.
In that case, it might be convenient to ignore some dependencies because you always want to use the latest version for those.
An example is the internal dependencies in an organization which should always use the latest version as opposed to third party dependencies which have a different upgrade cycle.

With this release, the `dependencyLocking` extension allows you to specify ignored dependencies:

```
dependencyLocking {
    ignoredDependencies.add("com.example:*")
}
```

With the above, any dependency in the `com.example` group will be ignored by the lock state validation or writing.

See the documentation for more details on [ignored dependencies in locking](userguide/dependency_locking.html#ignoring_dependencies).

## Configuration cache improvements

Gradle 6.6 introduced [configuration caching](userguide/configuration_cache.html) as an experimental feature.
This release comes with usability and performance improvements for the configuration cache.

Early adopters of configuration cache can use the command line output and HTML report for [troubleshooting](userguide/configuration_cache.html#config_cache:troubleshooting).
Previously, the configuration cache state was saved despite reported problems, which in some situation required manual cache invalidation.
In this release, the configuration cache gets discarded when the build fails because of configuration cache problems.
Note that you can still [ignore](userguide/configuration_cache.html#config_cache:usage:ignore_problems) known problems.

The problem report is now more helpful.
It reports the source of problems more accurately, pointing at the offending location in plugins and scripts in more cases.

Loading from the configuration cache is now faster and memory consumption during builds has been reduced, especially for Kotlin and Android builds.

Read about this feature and its impact [on the Gradle blog](https://blog.gradle.org/introducing-configuration-caching). Track progress of configuration cache support by [core plugins](https://github.com/gradle/gradle/issues/13454) and [community plugins](https://github.com/gradle/gradle/issues/13490).

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

* [File system watching is now stable](#file-system-watching-is-ready-for-production-use)

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
