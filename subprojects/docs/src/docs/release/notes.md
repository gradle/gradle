## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Continuous build now works with composite builds

Gradle's [continuous build feature](userguide/continuous_build.html) now works with [composite builds](userguide/composite_builds.html). Gradle will automatically detect changes to any input from any build and rebuild the appropriate pieces.

### CodeNarc plugin supports report format 'console'

The CodeNarc plugin now supports outputting reports directly to the console through the `console` report format.
```
codenarc {
    reportFormat = 'console'
}
```

### Faster Gradle command line client

The Gradle command line client now starts up ~200ms faster, speeding up every build.

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
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to handling of project dependencies from project that does not use the Java plugin to a project that does

When a project that does not use the Java plugin has a project dependency on a project that uses the Java plugin, either directly or indirectly via another plugin, then the `runtimeElements` configuration of the target project will be selected. Previous versions of Gradle would select the `default` configuration in this case.

Previous versions of Gradle would select the `runtimeElements` when both projects are using the Java plugin.

This change makes the selection behaviour consistent so that the `runtimeElements` configuration is selected regardless of whether the consuming project uses the Java plugin or not. This is also consistent with the selection when the consuming project is using one of the Android plugins.

### Changes to setTestNameIncludePatterns() of Test task

`setTestNameIncludePatterns` method of `Test` task is designed to be used in command line, therefore it will override configurations of `include`/`exclude` in `build.gradle` according to [Test filtering](userguide/java_plugin.html#test_filtering).

However, in previous versions of Gradle, this overriding mechanism is not implemented. Now, if `setTestNameIncludePatterns` is invoked directly, it will disable `include`/`exclude` defined in `build.gradle`. If you're affected by this change, use `filter.setIncludePatterns` instead.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

 - [Jörn Huxhorn](https://github.com/huxi) - Replace uses of `Stack` with `ArrayDeque` (#771)
 - [Björn Kautler](https://github.com/Vampire) - Fix WTP component version (#2076)
 - [Bo Zhang](https://github.com/blindpirate) - Add support for 'console' output type of CodeNarc plugin (#2170)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
