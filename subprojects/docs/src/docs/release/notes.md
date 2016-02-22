## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Automatic plugin classpath injection for TestKit

Previous versions of Gradle required significant amounts of boilerplate code for [injecting the plugin classpath](userguide/test_kit.html#sub:test-kit-classpath-injection). 
This version of Gradle provides a direct integration of TestKit with the [Java Gradle Plugin Development Plugin](userguide/javaGradle_plugin.html). By applying the plugin, 
declaring the `gradleTestKit()` dependency and injecting code under test happens automatically under the covers. As result, you don't have to write boilerplate code anymore
to functionally test your plugin code. To apply the Java Gradle Plugin Development Plugin to your project, add the following code to your build script.

    apply plugin: 'java-gradle-plugin'

You can read more about this functionality in the [user guide](userguide/test_kit.html#sub:test-kit-automatic-classpath-injection).

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

* [Alexander Afanasyev](https://github.com/cawka) - allow configuring java.util.logging in tests ([GRADLE-2524](https://issues.gradle.org/browse/GRADLE-2524))

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
