The Gradle team is excited to announce Gradle 5.3.

This release features incubating support for publishing and consuming Gradle Module Metadata,
[feature variants AKA "optional dependencies"](#feature-variants-aka-optional-dependencies),
type-safe accessors in Kotlin pre-compiled script plugins, and more.

We would like to thank the following community contributors to this release of Gradle:

[Stefan M.](https://github.com/StefMa), 
[Evgeny Mandrikov](https://github.com/Godin), 
[Simon Legner](https://github.com/simon04),  
[Raman Gupta](https://github.com/rocketraman),
[Florian Dreier](https://github.com/DreierF),
[Kenzie Togami](https://github.com/kenzierocks),
[Ricardo Pereira](https://github.com/thc202),
[Thad House](https://github.com/ThadHouse),
[Joe Kutner](https://github.com/jkutner),
... TBD ... 
and [Josh Soref](https://github.com/jsoref).

## Upgrade Instructions

Switch your build to use Gradle 5.3 RC1 by updating your wrapper properties:

`./gradlew wrapper --gradle-version=5.3-rc-1`

Standalone downloads are available at [gradle.org/release-candidate](https://gradle.org/release-candidate). 

## Feature variants, aka optional dependencies

Gradle now provides a powerful model for declaring features a library provides, known as [feature variants](userguide/feature_variants.html) :

```groovy
java {
   // declare an "optional feature"
   registerFeature("mysqlSupport") {
       usingSourceSet(sourceSets.main)
   }
}
dependencies {
   // declare dependencies specific to the "optional feature"
   mysqlSupportImplementation "mysql:mysql-connector-java:8.0.14"
}
```

Long story short, this can be used to model [optional dependencies](https://github.com/gradle/gradle/issues/867)!

## Better help message on delete operation failure

The `:clean` task, all `Delete` tasks, and `project.delete {}` operations now provide a better help message when failing to delete files. The most frequent and hard to troubleshoot causes for failing to delete files are other processes holding file descriptors open, and concurrent writes.

The help message now displays each failed path, which may be helpful in identifying which process might be holding files open, and will also display any files that were created in the target directory after a delete failure, which may be helpful in identifying when a process is still writing to the directory.

For example, a process holding `some.file` open in your `build` directory while running the `:clean` task would cause the following message to be displayed:

```
* What went wrong:
Execution failed for task ':clean'.
> Unable to delete directory '/path/to/your/gradle-project/build'
    Failed to delete some children. This might happen because a process has files open or has its working directory set in the target directory.
    - /path/to/your/gradle-build/subproject/build/some.file
```

A process still writing to your `build` directory while running the `:clean` task would display:

```
* What went wrong:
Execution failed for task ':clean'.
> Unable to delete directory '/path/to/your/gradle-project/build'
    New files were found. This might happen because a process is still writing to the target directory.
    - /path/to/your/gradle-build/subproject/build/new.file
```

## Improvements for plugin authors

### Use abstract types

- TBD - Abstract service injection getter methods
- TBD - Abstract mutable property
- TBD - Abstract `ConfigurableFileCollection` property
- TBD - Use an interface for Gradle instantiated types

### Factory method for creating `ConfigurableFileCollection` instances using `ObjectFactory`

Plugin and task implementations often need to create instances of various useful types, to provide a configurable model and DSL that is consistent with other Gradle plugins. One such type is `ConfigurableFileCollection`. In previous releases, plugins could use `Project.files()` or `ProjectLayout.configurableFiles()` to create instance of this type. However, these interfaces are not always available, for example in a `Settings` plugin (rather than a `Project` plugin) or in a nested model object.

In this release, plugin authors can use the `ObjectFactory.fileCollection()` method to create instances. The `ObjectFactory` service is used by plugin and task implementations to create objects of various useful types. This now includes instances of `ConfigurableFileCollection`.

## Default JaCoCo version upgraded to 0.8.3

[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to use [JaCoCo version 0.8.3](http://www.jacoco.org/jacoco/trunk/doc/changes.html) instead of 0.8.2 by default.

## Default Checkstyle version upgraded to 8.17

[The Checkstyle plugin](userguide/checkstyle_plugin.html) has been upgraded to use [Checkstyle version 8.17](http://checkstyle.sourceforge.net/releasenotes.html#Release_8.17) by default.

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

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 6.0). See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### Incubating method `ProjectLayout.configurableFiles()` replaced by `ObjectFactory.fileCollection()`

The method `ProjectLayout.configurableFiles()` is now deprecated, and will be removed in Gradle 6.0. You should use `ObjectFactory.fileCollection()` instead.

### Breaking changes

<!-- summary and links -->

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html#changes_5.3) to learn about breaking changes and considerations when upgrading to Gradle 5.3.

<!-- Do not add breaking changes here! Add them to the upgrade guide instead. --> 

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
