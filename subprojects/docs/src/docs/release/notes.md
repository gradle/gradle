The Gradle team is excited to announce Gradle 5.2.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
[Thomas Broyer](https://github.com/tbroyer).

<!-- 
## 1

details of 1

## 2

details of 2

## n
-->

## The Java Platform plugin

A new plugin, the [Java Platform plugin](userguide/java_platform_plugin.html) allows the declaration and publication of platforms for the Java ecosystem.
A platform is typically published as a bill-of-material (BOM) file, and can be used as a source of recommendations for versions, between projects or externally.
Read the [Java Platform plugin section of the userguide](userguide/java_platform_plugin.html) for more details.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

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

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html) to learn about breaking changes and considerations for upgrading from Gradle 5.x.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Thomas Broyer](https://github.com/tbroyer) - Provide default value for annotationProcessorGeneratedSourcesDirectory (gradle/gradle#7551)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## Upgrade Instructions

Switch your build to use Gradle 5.2 by updating your wrapper properties:

`./gradlew wrapper --gradle-version=5.2`

Standalone downloads are available at [gradle.org/release-candidate](https://gradle.org/release-candidate). 

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
