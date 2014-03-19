## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
### Example new and noteworthy
-->

### Easier debugging of JVM `Test` and `JavaExec` processes (i)

The [`Test`](dsl/org.gradle.api.tasks.testing.Test.html) and [`JavaExec`](dsl/org.gradle.api.tasks.JavaExec.html) tasks both now support a `--debugJvm` invocation time switch, which is equivalent
to setting the `debug` property of these tasks to `true`.

This makes it easy, for example, to launch the application in debug mode when using the [Application plugin](userguide/application_plugin.html)…

<pre><tt>gradle run --debugJvm</tt></pre>

This starts the JVM process in debug mode, and halts the process until a debugger attaches on port 5005.
The same can be done for any [`Test`](dsl/org.gradle.api.tasks.testing.Test.html) task.

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

### Custom TestNG listeners are applied before Gradle's listeners

This way the custom listeners are more robust because they can affect the test status.
There should be no impact of this change because majority of users does not employ custom listeners
and even if they do healthy listeners will work correctly regardless of the listeners' order.

### Change to signature of `Test.filter(Closure)`

The incubating `Test.filter(Closure)` method introduced in 1.10 for configuring the `TestFilter` has been changed to be more consistent with other configuration methods.
This method now accepts an `Action` and no longer returns the `TestFilter`.
This change should not require any adjustments to build scripts as this method can still be called with a `Closure`, upon which it will be implicitly converted into an `Action`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
