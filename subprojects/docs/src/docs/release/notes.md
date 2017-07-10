The Gradle team is pleased to announce the release of Gradle 4.1.

This release now supports running Gradle on the most recent JDK 9 release (b170+). It also optimizes startup speed, positively affecting the execution time of every build.

## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->


### Faster Gradle command line client

The Gradle command line client now starts up ~200ms faster, speeding up every build.

### Continuous build now works with composite builds

Gradle's [continuous build feature](userguide/continuous_build.html) now works with [composite builds](userguide/composite_builds.html). Gradle will automatically detect changes to any input from any build and rebuild the appropriate pieces.

### CodeNarc plugin supports report format 'console'

The CodeNarc plugin now supports outputting reports directly to the console through the `console` report format.
```
codenarc {
    reportFormat = 'console'
}
```

### APIs to define calculated task input and output locations

TBD - This release builds on the `Provider` concept added in Gradle 4.0 to add conveniences that allow plugins and build scripts to define task input and output locations that are calculated lazily. For example, a common problem when implementing a plugin is how to define task output locations relative to the project's build directory in a way that deals with changes to the build directory location later during project configuration.

- Added `Directory` and `RegularFile` abstractions and providers to represent locations that are calculated lazily.
- Added a `ProjectLayout` service that allows input and output locations to be defined relative to the project's project directory and build directory. 
- `Project.file()` and `Project.files()` can resolve `Provider` instances to `File` and `FileCollection` instances.

### Console displays parallel test execution

With this release of Gradle, the console displays any test worker processes executed in parallel in the [work in-progress area](userguide/console.html#sec:console_work_in_progress_display). Every worker process can be identified by an ID e.g. `Gradle Test Executor 7`. Each test executor line also indicates the test class it is currently working on. At the moment only JVM-based test worker processes supported by Gradle core (that is JUnit and TestNG) are rendered in parallel in the console. The display of the overall test count of a `Test` task stays unchanged. 

    <========-----> 69% EXECUTING [23s]
    > IDLE
    > :plugin:functionalTest > 127 completed, 2 skipped
    > :other:compileJava
    > :plugin:functionalTest > Gradle Test Executor 7 > Executing test org.gradle.plugin.ConsoleFunctionalTest
    > :fooBarBazQuux:test > 3 completed
    > :plugin:functionalTest > Gradle Test Executor 8 > Executing test org.gradle.plugin.UiLayerFunctionalTest
    > IDLE
    > :fooBarBazQuux:test > Gradle Test Executor 6 > Executing test org.gradle.MyTest

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

### Changes to handling of project dependencies from a project that does not use the Java plugin to a project that does

When a project that does not use the Java plugin has a project dependency on a project that uses the Java plugin, either directly or indirectly via another plugin, then the `runtimeElements` configuration of the target project will be selected. Previous versions of Gradle would select the `default` configuration in this case.

Previous versions of Gradle would select the `runtimeElements` when both projects are using the Java plugin.

This change makes the selection behaviour consistent so that the `runtimeElements` configuration is selected regardless of whether the consuming project uses the Java plugin or not. This is also consistent with the selection when the consuming project is using one of the Android plugins.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Jörn Huxhorn](https://github.com/huxi) - Replace uses of `Stack` with `ArrayDeque` (#771)
 - [Björn Kautler](https://github.com/Vampire) - Fix WTP component version (#2076)
 - [Bo Zhang](https://github.com/blindpirate) - Add support for 'console' output type of CodeNarc plugin (#2170)
 - [Bo Zhang](https://github.com/blindpirate) - Fix infinite loop when using `Path` for task property (#1973)
 - [Marcin Zajączkowski](https://github.com/szpak) - Add `@since` tag to `Project.findProperty()` (#2403)
 - [Seth Jackson](https://github.com/sethjackson) - Fix the default daemon JVM args on Java 8 (#2310)
 - [Ismael Juma](https://github.com/ijuma) - Update default Zinc compiler version to 0.3.15 with preliminary Java 9 support (#2420)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
