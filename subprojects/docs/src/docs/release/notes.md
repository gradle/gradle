## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Software model changes

TBD - Binary names are now scoped to the component they belong to. This means multiple components can have binaries with a given name. For example, several library components
might have a `jar` binary. This allows binaries to have names that reflect their relationship to the component, rather than their absolute location in the software model.

### Visualising a project's build script dependencies

The new `buildEnvironment` task can be used to visualise the project's `buildscript` dependencies.
This task is implicitly available for all projects, much like the existing `dependencies` task.

The `buildEnvironment` task can be used to understand how the declared dependencies of project's build script actually resolve,
including transitive dependencies.

The feature was kindly contributed by [Ethan Hall](https://github.com/ethankhall).

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

### Changes to incubating software model

TBD

- `BinarySpec.name` should no longer be considered a unique identifier for the binary within a project.
- The name for the 'build' task for a binary is now qualified with the name of its component. For example, `jar` in `mylib` will have a build task called 'mylibJar'
- The name for the compile tasks for a binary is now qualified with the name of its component.
- JVM libraries have a binary called `jar` rather than one qualified with the library name.
- When building a JVM library with multiple variants, the task and output directory names have changed. The library name is now first.

### Changes to incubating native software model

- Task names have changed for components with multiple variants. The library or executable name is now first.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Ethan Hall](https://github.com/ethankhall) - Addition of new `buildEnvironment` task

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
