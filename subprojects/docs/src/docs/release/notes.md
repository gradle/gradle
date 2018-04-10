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

### Better control of native system headers

In previous versions of Gradle, the native compile task include path was a single monolithic collection of files that was accessible through the `includes` property on the compile task.
However, in Gradle 4.8, system header include directories can now be accessed separately via the `systemIncludes` property.  This allows the user fine-grained control over which system headers are used at compile time.
Furthermore, on GCC-compatible toolchains, the system header include directories specified with `systemIncludes` will be specified on the command line using the ["-isystem" argument](https://gcc.gnu.org/onlinedocs/gcc/Directory-Options.html) which marks them for special treatment by the compiler.

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

## Potential breaking changes

<!--
### Example breaking change
-->

### TaskContainer.remove() now actually removes the task

TBD - previously this was broken, and plugins may accidentally rely on this behaviour.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Florian Nègre](https://github.com/fnegre) Fix distribution plugin documentation (gradle/gradle#4880)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
