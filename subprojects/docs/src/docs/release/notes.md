## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Java software model compile avoidance

This version of Gradle further optimizes on avoiding recompiling consuming libraries after non-ABI breaking changes. Since 2.9, if a library declares an API, Gradle creates a "[stubbed API jar](userguide/java_software.html)". This enables avoiding recompiling any consuming library if the application binary interface (ABI) of the library doesn't change. This version of Gradle extends this functionality to libraries that don't declare their APIs, speeding up builds with incremental changes in most Java projects, small or large. In particular, a library `A` that depend on a library `B` will not need to be recompiled in the following cases:

* a private method is added to `B`
* a method body is changed in `B`
* order of methods is changed in `B`

This feature only works for local libraries, not external dependencies. More information about compile avoidance can be found in the [userguide](userguide/java_software.html).

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

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Johnny Lim](https://github.com/izeye) - documentation improvement

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
