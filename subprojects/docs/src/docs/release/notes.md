The Gradle team is excited to announce Gradle 5.3.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:

[Stefan M.](https://github.com/StefMa), [Evgeny Mandrikov](https://github.com/Godin), [Simon Legner](https://github.com/simon04),  
... TBD ... 
and [Josh Soref](https://github.com/jsoref).

## Upgrade Instructions

Switch your build to use Gradle 5.3 by updating your wrapper properties:

`./gradlew wrapper --gradle-version=5.3`

Standalone downloads are available at [gradle.org/releases](https://gradle.org/releases). 

## Improvements for plugin authors

- TBD - Abstract service injection getter methods
- TBD - Abstract mutable property
- TBD - Abstract `ConfigurableFileCollection` property
- TBD - Use an interface for Gradle instantiated types

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

## Default JaCoCo version upgraded to 0.8.3

[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to use [JaCoCo version 0.8.3](http://www.jacoco.org/jacoco/trunk/doc/changes.html) instead of 0.8.2 by default.

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

<!--
### Example deprecation
-->

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
