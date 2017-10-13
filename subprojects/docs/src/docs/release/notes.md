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
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Support for Maven optional dependencies

Gradle will now [take Maven optional dependencies into account](https://github.com/gradle/gradle/pull/3129) during dependency resolution. 
Before, optional dependencies were not included in the dependency graph.
As of Gradle 4.4, optional dependencies will participate in dependency resolution as soon as another dependency on the same module is found in the graph.
For example, if a transitive dependency on `foo:bar:1.1` is optional, but another path in the dependency graph brings `foo:bar:1.0` (not optional), then Gradle will resolve to `foo:bar:1.1`.
Previous releases would resolve to `foo:bar:1.0`. However, if no "hard" dependency is found on the optional module, then it will **not** be included, as previous Gradle versions did.
It means that depending on the shape of your dependency graph, you may now have a different dependency resolution result after upgrading to Gradle 4.4.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

- [Kyle Moore](https://github.com/DPUkyle) - updated Gosu plugin to fix API breakage (gradle/gradle#3115)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
