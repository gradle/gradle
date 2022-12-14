The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THiS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->
We would like to thank the following community members for their contributions to this release of Gradle:

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->



#### Run `buildSrc` tasks
#### Improvements for `buildSrc`
#### Improvements for `buildSrc` builds
This release includes several improvements for [`buildSrc`](userguide/organizing_gradle_projects#sec:build_sources) builds to make them behave similarly to an [included build](userguide/composite_builds#composite_build_intro).

##### Run `buildSrc` tasks directly
It is now possible to run the tasks of `buildSrc` from the command-line, using the same syntax used for the tasks of included builds.
For example, you can use `gradle buildSrc:build` to run the `build` task in the `buildSrc` build.

TODO - link to running included build tasks

#### `buildSrc` can include other builds
The `buildSrc` build can now include other builds by declaring them in `buildSrc/settings.gradle.kts` or `buildSrc/settings.gradle`.
You can use `pluginsManagement { includeBuild(someDir) }` or `includeBuild(someDir)` in this settings script to make other builds available for `buildSrc`

TODO - link to declaring included builds

#### Tests for `buildSrc` are no longer automatically run
When Gradle builds the output of `buildSrc` it only runs the tasks that produce that output. It no longer runs the `build` task.
In particular, this means that the tests of `buildSrc` and its subprojects are not built and executed when they are not needed.

TODO - you can run these tasks from the command-line or edit buildSrc to restore the old behaviour; link to upgrade guide 

#### Init scripts are applied to `buildSrc`
Init scripts specified on the command-line using `--init-script` are now applied to `buildSrc`, in addition to the main build and all included builds.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

#### Improved Gradle User Home Cache Cleanup
Previously, cleanup of the caches in Gradle User Home used fixed retention periods (30 days or 7 days depending on the cache).  
These retention periods can now be configured via the [Settings](dsl/org.gradle.api.initialization.Settings.html) object in an init script in Gradle User Home.

```groovy
beforeSettings { settings ->
    settings.caches {
        downloadedResources.removeUnusedEntriesAfterDays = 45
    }
}
```

Furthermore, it was previously only possible to partially disable cache cleanup via the `org.gradle.cache.cleanup` Gradle property in Gradle User Home.  
Disabling cache cleanup now affects more caches under Gradle User Home and can also be configured via the [Settings](dsl/org.gradle.api.initialization.Settings.html) object in an init script in Gradle User Home.

```groovy
beforeSettings { settings ->
    settings.caches {
        cleanup = Cleanup.DISABLED
    }
}
```

See [Configuring cleanup of caches and distributions](userguide/directory_layout.html#dir:gradle_user_home:configure_cache_cleanup) for more information.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
