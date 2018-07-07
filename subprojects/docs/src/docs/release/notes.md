## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Use SNAPSHOT plugin versions with the `plugins {}` block

Starting with this release, it is now possible to use SNAPSHOT plugin versions in the `plugins {}` and `pluginManagement {}` blocks.

### Nested included builds

Composite builds is a feature that allows a Gradle build to 'include' another build and conveniently use its outputs locally rather than via a binary repository. This makes some common workflows more convenient, such as working on multiple source repositories at the same time to implement a cross-cutting feature. In previous releases, it was not possible for a Gradle build to include another build that also includes other builds, which limits the usefulness of this feature for these workflows. In this Gradle release, a build can now include another build that also includes other builds. In other words, composite builds can now be nested.

There are a number of limitations to be aware of. These will be improved in later Gradle releases:

- A `buildSrc` build cannot include other builds, such as a shared plugin build.
- The root project of each build must have a unique name.

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

### Creating instances of JavaPluginConvention

Instances of this class are intended to be created only by the `java-base` plugin and should not be created directly. Creating instances using the constructor of `JavaPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `java-base` plugin.

### Creating instances of ApplicationPluginConvention

Instances of this class are intended to be created only by the `application` plugin and should not be created directly. Creating instances using the constructor of `ApplicationPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `application` plugin.

## Potential breaking changes

### Kotlin DSL breakages

- `project.java.sourceSets` is now `project.sourceSets`

## External contributions


We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

- [Björn Kautler](https://github.com/Vampire) - Update Spock version in docs and build init (gradle/gradle#5627)
- [Kyle Moore](https://github.com/DPUkyle) - Use latest Gosu plugin 0.3.10 (gradle/gradle#5855)
- [Mata Saru](https://github.com/matasaru) - Add missing verb into docs (gradle/gradle#5694)
- [Sébastien Deleuze](https://github.com/sdeleuze) - Add support for SNAPSHOT plugin versions in the `plugins {}` block (gradle/gradle#5762)
- [Ben McCann](https://github.com/benmccann) - Decouple Play and Twirl versions (gradle/gradle#2062)

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
