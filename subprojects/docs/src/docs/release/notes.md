## New and noteworthy

Here are the new features introduced in this Gradle release.

### Child processes started by Gradle are better described

At the [Gradle Summit 2014 Conference](http://www.gradlesummit.com/conference/santa_clara/2014/06/home)
we ran a [Contributing To Gradle Workshop](http://www.gradlesummit.com/conference/santa_clara/2014/06/session?id=31169).
During the session, [Rob Spieldenner](https://github.com/rspieldenner)
contributed a very nice feature that gives much better insight into the child processes started by Gradle.
The example output of `jps -m` command now also contains the function of the worker process:

    28649 GradleWorkerMain 'Gradle Test Executor 17'
    28630 GradleWorkerMain 'Gradle Compiler Daemon 1'

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

### Changed Java compiler integration for joint Java - Scala compilation

The `ScalaCompile` task type now uses the same Java compiler integration as the `JavaCompile` and `GroovyCompile` task types for performing joint Java - Scala
compilation. Previously it would use the old Ant-based Java compiler integration, which is no longer supported in the Gradle 2.x stream.

This change should be backwards compatible for all users, and should improve compilation time when compiling Java and Scala together.

### Incubating native language plugins no longer apply the base plugin

The native language plugins now apply the [`LifecycleBasePlugin`](dsl/org.gradle.language.base.plugins.LifecycleBasePlugin) instead of the `BasePlugin`. This means
that the default values defined by the `BasePlugin` are not available.

TBD - make this more explicit re. what is actually not longer available.

### Internal methods removed

- The internal method `Javadoc.setJavadocExecHandleBuilder()` has been removed. You should use `setToolChain()` instead.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Rob Spieldenner](https://github.com/rspieldenner) - Made the worker processes better described in the process list.

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
