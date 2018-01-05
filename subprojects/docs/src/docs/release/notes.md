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

### Local build cache directory cleanup is now time-based

Previously, Gradle would clean up the [local build cache directory](userguide/build_cache.html#sec:build_cache_configure_local) only if the size of its contents reached 5 GB, or whatever was configured in `targetSizeInMB`.
From now on Gradle will instead clean up everything older than 7 days, regardless of the size of the cache directory.
As a consequence `targetSizeInMB` is now deprecated, and changing its value has no effect.

The minimum age for entries to be cleaned up can now be configured in `settings.gradle` via the [`removeUnusedEntriesAfterDays`](dsl/org.gradle.caching.local.DirectoryBuildCache.html#org.gradle.caching.local.DirectoryBuildCache:removeUnusedEntriesAfterDays) property:

```
buildCache {
    local {
        removeUnusedEntriesAfterDays = 30
    }
}
```

## Potential breaking changes

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
