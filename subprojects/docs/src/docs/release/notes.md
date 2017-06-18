## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### APIs to define calculated task input and output locations

TBD - This release builds on the `Provider` concept added in Gradle 4.0 to add conveniences that allow plugins and build scripts to define task input and output locations that are calculated lazily. For example, a common problem when implementing a plugin is how to define task output locations relative to the project's build directory in a way that deals with changes to the build directory location later during project configuration.

- Added `Directory` and `RegularFile` abstractions and providers to represent locations that are calculated lazily.
- Added a `ProjectLayout` service that allows input and output locations to be defined relative to the project's project directory and build directory. 
- `Project.file()` and `Project.files()` can resolve `Provider` instances to `File` and `FileCollection` instances.

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

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Bo Zhang](https://github.com/blindpirate) - Fix infinite loop when using `Path` for task property (#1973)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
