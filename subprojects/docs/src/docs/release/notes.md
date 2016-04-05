## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

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

### Gradle implementation dependencies are not visible to plugins at development time

Implementing a Gradle plugin requires the declaration of `gradleApi()`
to the `compile` configuration. The resolved dependency encompasses the
entire Gradle runtime including Gradle's third party dependencies
(e.g. Guava). Any third party dependencies declared by the plugin might
conflict with the ones pulled in by the `gradleApi()` declaration. Gradle
does not apply conflict resolution. As a result The user will end up with
two addressable copies of a dependency on the compile classpath and in
 the test runtime classpath.

In previous versions of Gradle the dependency `gradleTestKit()`, which
relies on a Gradle runtime, attempts to address this problem via class
relocation. The use of `gradleApi()` and `gradleTestKit()` together
became unreliable as classes of duplicate name but of different content
were added to the classpath.

With this version of Gradle proper class relocation has been implemented
 across the dependencies `gradleApi()`, `gradleTestKit()` and the published
 Tooling API JAR. Projects using any of those dependencies will not
 conflict anymore with classes from third party dependencies used by
 the Gradle runtime. Classes from third-party libraries provided by
 the Gradle runtime are no longer "visible" at compile and test
 time.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Igor Melnichenko](https://github.com/Myllyenko) - fixed Groovydoc up-to-date checks ([GRADLE-3349](https://issues.gradle.org/browse/GRADLE-3349))
- [Sandu Turcan](https://github.com/idlsoft) - add wildcard exclusion for non-transitive dependencies in POM ([GRADLE-1574](https://issues.gradle.org/browse/GRADLE-1574))

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
