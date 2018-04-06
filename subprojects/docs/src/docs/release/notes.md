## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Better control over system include path for native compilation

In previous versions of Gradle, the native compile task include path was a single monolithic collection of files that was accessible through the `includes` property on the compile task.
In Gradle 4.8, system header include directories can now be accessed separately via the `systemIncludes` property. 
On GCC-compatible toolchains, the system header include directories specified with `systemIncludes` will be specified on the command line using the ["-isystem" argument](https://gcc.gnu.org/onlinedocs/gcc/Directory-Options.html), which marks them for special treatment by the compiler.   

### Upgradation of CodeNarc

The default version of `CodeNarc` is `1.1` now.

### Signing Publications

The Signing plugin now supports signing all artifacts of a publication, e.g. when publishing artifacts to a Maven or Ivy repository.

    publishing {
        publications {
            mavenJava(MavenPublication) {
                from components.java
            }
        }
    }

    signing {
        sign publishing.publications
    }

### Published Ivy descriptors now contain configuration-wide dependency exclusions

The [Ivy Publishing Plugin](userguide/publishing_ivy.html) now writes dependency exclude rules defined on a configuration (instead of on an individual dependency) into the generated Ivy module descriptor.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Methods on `FileCollection`

- `FileCollection.add()` is now deprecated. Use `ConfigurableFileCollection.from()` instead. You can create a `ConfigurableFileCollection` via `Project.files()`.
- `FileCollection.stopExecutionIfEmpty()` is deprecated without a replacement. You can use `@SkipWhenEmpty` on a `FileCollection` property, or throw a `StopExecutionException` in your code manually instead.

### Method on `Signature`

`Signature.getToSignArtifact()` should have been an internal API and is now deprecated without a replacement.

## Potential breaking changes

### Changed behaviour for missing init scripts

In previous releases of Gradle, an init script specified on the command line that did not exist would be silently ignored. In this release, the build will fail if any of the init scripts specified on the command line does not exist.

<!--
### Example breaking change
-->

### TaskContainer.remove() now actually removes the task

TBD - previously this was broken, and plugins may accidentally rely on this behaviour.

### Signature.setFile() no longer changes the file to be published

Previously, `Signature.setFile()` could be used to replace the file used for publishing a `Signature`. However, the actual signature file was still being generated at its default location. Therefore, `Signature.setFile()` is now deprecated and will be removed in a future release.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Florian Nègre](https://github.com/fnegre) Fix distribution plugin documentation (gradle/gradle#4880)
- [Patrik Erdes](https://github.com/patrikerdes) Fail the build if a referenced init script does not exist (gradle/gradle#4845)
- [Emmanuel Debanne](https://github.com/debanne) Upgrade CodeNarc to version 1.1 (gradle/gradle#4917)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
