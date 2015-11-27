## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Java software model compile avoidance

This version of Gradle now creates a "[stubbed API jar](userguide/java_software.html)" instead of a copy of the runtime jar when a JVM library doesn't declare any API, just like libraries that do declare an API. As a consequence, libraries that do not declare APIs can also now benefit from compile avoidance in case the application binary interface (ABI) doesn't change. That is to say that libraries that depend on another library that does not declare an API will not need to be recompiled in the following cases:

* a private method is added
* a method body is changed
* order of methods is changed

More information about compile avoidance can be found in the [userguide](userguide/java_software.html).

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
