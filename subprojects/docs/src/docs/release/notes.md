## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Play Framework Support (i)

Gradle now supports building [Play](https://www.playframework.com/) applications for Play versions 2.2.x and 2.3.x.

See the new user guide section about using the [`play` plugin](userguide/play_plugin.html).

TBD

### Support for verifying Gradle wrapper distribution download against SHA-256 hash

It is now possible to verify the integrity of the Gradle distribution downloaded by the [Gradle wrapper](userguide/gradle_wrapper.html) against
a known SHA-256 hash.

To enable wrapper verification you need only specify a `distributionSha256Sum` property in your project's `gradle-wrapper.properties` file.

    distributionSha256Sum=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

Please see section [62.2. Verification of downloaded Gradle distributions](userguide/gradle_wrapper.html#sec:verification) of the User Guide for more information.

This feature was contributed by [Dominik Schürmann](https://github.com/dschuermann).

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Component model changes

* Removed `BinarySpec.source(Object)`: It is no longer possible to add a sourceSet from one binary/component to another binary.
* `@Managed` models are no longer permitted to have setter methods for members of type `ManagedSet`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Steve Ebersole](https://github.com/sebersole) - Support for passing arbitrary arguments to FindBugs tasks
* [Malte Finsterwalder](https://github.com/finsterwalder) - Fixed resolving of references to `${parent.artifactId}` in POM files (GRADLE-3299)
* [Roy Kachouh](https://github.com/roykachouh) - Fix for Application plugin script generation in projects with alphanumeric names
* [Sebastian Schuberth](https://github.com/sschuberth) - Documentation improvements
* [Andrew Shu](https://github.com/talklittle) - Documentation improvements
* [Dominik Schürmann](https://github.com/dschuermann) - Support for verifying Gradle wrapper distribution download against SHA-256 hash

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
