## New and noteworthy

Here are the new features introduced in this Gradle release.

### Daemon health monitoring

The daemon actively monitors its health and may expire earlier if its performance degrades.
The current implementation monitors the overhead of garbage collector and may detect memory issues.
Memory problems can be caused by 3rd party plugins written without performance review.
We want the Gradle daemon to be rock solid and enabled by default in the future.
This feature is a big step forward towards the goal.
Down the road the health monitoring will get richer and smarter, providing the users the insight into daemon's performance
and deciding whether to restart the daemon process.

Incubating system property "org.gradle.daemon.performance.logging" can be used to switch on an elegant message emitted at the beginning of each build.
The new information presented in the build log helps getting better understanding of daemon's performance:

    Starting 3rd build in daemon [uptime: 15 mins, performance: 92%, memory: 65% of 1.1 GB]

The logging can be turned on by tweaking "org.gradle.jvmargs" property of the gradle.properties file:

    org.gradle.jvmargs=-Dorg.gradle.daemon.performance.logging=true

### Improved performance with class loader caching

We want each new version of Gradle to perform better.
Gradle is faster and less memory hungry when class loaders are reused between builds.
The daemon process can cache the class loader instances, and consequently, the loaded classes.
This unlocks modern jvm optimizations that lead to faster execution of consecutive builds.
This also means that if the class loader is reused, static state is preserved from the previous build.
Class loaders are not reused when build script classpath changes (for example, when the build script file is changed).

In the reference project, we observed 10% build speed improvement for the initial build invocations in given daemon process.
Later build invocations perform even better in comparison to Gradle daemon without classloader caching.

This incubating feature is not turned on by default at the moment.
It can be switched on via incubating system property "org.gradle.caching.classloaders".
Example setting in gradle.properties file:

    org.gradle.jvmargs=-Dorg.gradle.caching.classloaders=true

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

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
