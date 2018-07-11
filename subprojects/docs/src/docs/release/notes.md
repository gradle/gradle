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

### Periodic cache cleanup

Caching has always been one of the strong suits of Gradle. Over time, more and more persistent caches have been added to improve performance and support new features, requiring more and more disk space on build servers and developer workstations. Gradle now addresses one of the most highly voted issues on GitHub and introduces the following cleanup strategies:

- Version-specific cache directories in `GRADLE_USER_HOME/caches/<gradle-version>/` are checked periodically (at most every 24 hours) for whether they are still in use. If not, directories for release versions are deleted after 30 days of inactivity, snapshot versions after 7 days of inactivity. Moreover, the corresponding Gradle distributions in `GRADLE_USER_HOME/wrapper/dists/` are deleted as well, if present.
- Similarly, after building a project, version-specific cache directories in `PROJECT_DIR/.gradle/<gradle-version>/` are checked periodically (at most every 24 hours) for whether they are still in use. They are deleted if they haven't been used for 7 days.
- Shared versioned cache directories in `GRADLE_USER_HOME/caches/` (e.g. `jars-*`) are checked periodically (at most every 24 hours) for whether they are still in use. If there's no Gradle version that still uses them, they are deleted.
- Files in shared caches used by the current Gradle version in `GRADLE_USER_HOME/caches/` (e.g. `jars-3` or `modules-2`) are checked periodically (at most every 24 hours) for when they were last accessed. Depending on whether the file can be recreated locally or would have to be downloaded from a remote repository again, it will be deleted after 7 or 30 days of not being accessed, respectively.


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

### Creating instances of WarPluginConvention

Instances of this class are intended to be created only by the `war` plugin and should not be created directly. Creating instances using the constructor of `WarPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `war` plugin.

### Creating instances of EarPluginConvention

Instances of this class are intended to be created only by the `ear` plugin and should not be created directly. Creating instances using the constructor of `EarPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `ear` plugin.

### Creating instances of BasePluginConvention

Instances of this class are intended to be created only by the `base` plugin and should not be created directly. Creating instances using the constructor of `BasePluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `base` plugin.

### Creating instances of ProjectReportsPluginConvention

Instances of this class are intended to be created only by the `project-reports` plugin and should not be created directly. Creating instances using the constructor of `ProjectReportsPluginConvention` will become an error in Gradle 5.0. The class itself is not deprecated and it is still be possible to use the instances created by the `project-reports` plugin.

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
- [Mike Kobit](https://github.com/mkobit) - Add ability to use `RegularFile` and `Directory` as publishable artifacts (gradle/gradle#5109)

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
