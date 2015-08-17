## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### PMD 'minimumPriority' configuration

By default, the PMD plugin will report all rule violations and fail if any violations are found.  This means the only way to disable low priority violations was to create a custom ruleset.

Gradle now supports configuring a "minimum priority" threshold.  The PMD report will contain only violations higher than or equal to the priority configured.

You configure the threshold via the [PmdExtension](dsl/org.gradle.api.plugins.quality.PmdExtension.html).  You can also configure the property on a per-task level through
[Pmd](dsl/org.gradle.api.plugins.quality.Pmd.html).

   pmd {
       minimumPriority = 3
   }

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

### Support for PMD versions <5.0

We have removed integration test coverage for PMD 4.3 (the last release of PMD before 5.0).  Newer PMD plugin features do not work with PMD 4.3 and the PMD check task does not
fail when finding violations.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Alpha Hinex](https://github.com/AlphaHinex) - Allow encoding to be specified for Zip task
* [Adam Roberts](https://github.com/AdamRoberts) - Specify minimum priority for PMD task

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
