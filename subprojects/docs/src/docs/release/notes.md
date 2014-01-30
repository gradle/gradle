## New and noteworthy

Here are the new features introduced in this Gradle release.

### CUnit integration (i)

TODO

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
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

### Deprecations in Tooling API communication

* Using Tooling API to connect to provider using older distribution than Gradle 1.0-milestone-8 is now deprecated and scheduled for removal in version Gradle 2.0.
<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to incubating native support

* '-Xlinker' is no longer automatically added to linker args for GCC or Clang. If you want to pass an argument directly to 'ld' you need to add this escape yourself.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Jesse Glick](https://github.com/jglick) - enabling newlines in option values passed to build
<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
