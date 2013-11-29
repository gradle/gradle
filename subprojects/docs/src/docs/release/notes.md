## New and noteworthy

Here are the new features introduced in this Gradle release.

### Improved configuration model for native components

- Can specify a set of target platforms for a component

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
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changed DSL and model for native binary components

Much of the model and DSL for defining native binary components has been changed. Most build scripts leveraging this functionality
will need to be updated.

- Gradle no longer creates a binary variant for every available tool chain. Instead, the variants of a component are defined
  by it's flavors, build types and target platforms. Gradle will attempt to locate a tool chain to build each possible variant.
- The set of configured platforms can be defined separately from the target platforms for a component. The set of all platforms
  is specified by the `platforms` container within the `model` block.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [“Big Guy”](https://github.com/big-guy) - internal test fixes on SELinux
* [Michael Putters](https://github.com/mputters) - don't show the version/copyright information when invoking the Windows resources compiler

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
