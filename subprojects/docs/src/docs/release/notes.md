## New and noteworthy

Here are the new features introduced in this Gradle release.

### Support for Precompiled Headers

Precompiled headers are a performance optimization for native builds that allows commonly used headers to be compiled only once rather than for
each file that includes the headers.  Precompiled headers are now supported for C, C++, Objective-C and Objective-C++ projects.

To use a precompiled header, a header file needs to defined containing all of the headers that should be precompiled.  This header file is
then declared in the build script as a precompiled header.

    model {
        components {
            hello(NativeLibrarySpec) {
                sources {
                    cpp {
                        preCompiledHeader "pch.h"
                    }
                }
            }
        }
    }

Each source set can have a single precompiled header defined.  Any source file that includes this header file as the first header will
be compiled using the precompiled header.  Otherwise, the precompiled header will be ignored and the source file will be compiled in the
normal manner.  Please see the [userguide](userguide/nativeBinaries.html#native_binaries:preCompiledHeaders) for further information.

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

### Distribution Plugin changes

Due to a bug in the distribution plugin (see GRADLE-3278), earlier Gradle versions didn't follow the general naming convention for the assemble task of the main distribution.
This has been fixed and assemble task name for the main distribution has changed from `assembleMainDist` to `assembleDist`.

### Removal of `componentSpec` project extension

As part of work on exposing more of the component model to rules the `componentSpec` project extension previously added by all language plugins via `ComponentModelBasePlugin` has been removed.
Currently component container can be only accessed using model rules.

### `model.components` cannot be viewed as
<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Daniel Lacasse](https://github.com/Shad0w1nk) - Support GoogleTest for testing C++ binaries
* [Lóránt Pintér](https://github.com/lptr), [Daniel Vigovszky](https://github.com/vigoo) and [Mark Vujevits](https://github.com/vujevits) - implement dependency substitution for projects

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
