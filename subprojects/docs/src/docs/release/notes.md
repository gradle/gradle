## New and noteworthy

Here are the new features introduced in this Gradle release.

### Dependency substitution rules

In previous Gradle versions you could use a 'Dependency resolve rule' to replace an external dependency with another:

    resolutionStrategy {
        eachDependency {
            if (it.requested.group == 'com.deprecated') {
                details.useTarget group: 'com.replacement.group', name: it.requested.module, version: it.requested.version
            }
        }
    }

This behaviour has been enhanced and extended, with the introduction of 'Dependency Substitution Rules'.
These rules allow an external dependency to be replaced with a project dependency, and vice-versa.

You replace a project dependency with an external dependency like this:

    resolutionStrategy {
        dependencySubstitution {
            withProject(":api") {
                useTarget group: "org.utils", name: "api", version: "1.3"
            }
        }
    }

And replace an external dependency with an project dependency like this:


    resolutionStrategy {
        dependencySubstitution {
            withModule("com.example:my-module") {
                useTarget project(":project1")
            }
        }
    }

There are other options available to match module and project dependencies:

    all { DependencySubstitution<ComponentSelector> details -> /* ... */ }
    eachModule() { ModuleDependencySubstitution details -> /* ... */ }
    withModule("com.example:my-module") { ModuleDependencySubstitution details -> /* ... */ }
    eachProject() { ProjectDependencySubstitution details -> /* ... */ }
    withProject(":api")) { ProjectDependencySubstitution details -> /* ... */ }

It is also possible to replace one project dependency with another, or one external dependency with another. (The latter provides the same functionality
as `eachDependency`).
Note that the `ModuleDependencySubstitution` has a convenience `useVersion()` method. For the other substitutions you should use `useTarget()`.

### Support for Precompiled Headers (i)

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

### Google Test support (i)

- TBD

### Task group accessible from the Tooling API

Tasks in Gradle may define a _group_ attribute, but this group wasn't accessible from the Tooling API before. It is now possible to query the
group of a task through `org.gradle.tooling.model.Task#getGroup`.

### Increased visibility of components in model report

- TBD

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

### Changing a configuration after it has been resolved

TODO

### Distribution Plugin changes

Due to a bug in the distribution plugin (see GRADLE-3278), earlier Gradle versions didn't follow the general naming convention for the assemble task of the main distribution.
This has been fixed and assemble task name for the main distribution has changed from `assembleMainDist` to `assembleDist`.

### Changes in ComponentModelBasePlugin

#### Removal of `componentSpec` project extension

As part of work on exposing more of the component model to rules the `componentSpec` project extension previously added by all language plugins via `ComponentModelBasePlugin` has been removed.
Currently component container can be only accessed using model rules.

#### Changes in `ComponentSpecContainer`

- `ComponentSpecContainer` no longer implements `ExtensiblePolymorphicDomainObjectContainer<ComponentSpec>`.
- `ComponentSpecContainer` now implements `CollectionBuilder<ComponentSpec`.
- All configuration done using subject of type `ComponentSpecContainer` is now deferred. In earlier versions of Gradle it was eager.

### Changes in NativeBinariesTestPlugin

- `TestSuiteContainer` no longer implements `ExtensiblePolymorphicDomainObjectContainer<TestSuiteSpec>`.
- `TestSuiteContainer` now implements `CollectionBuilder<TestSuiteSpec`.

### Source sets and binaries cannot be removed from components

### Type of binaries container of `ComponentSpec` has changed from `DomainObjectSet<BinarySpec>` to `NamedDomainObjectSet<BinarySpec>`

### Configurations that are task inputs are resolved before building the task execution graph

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Daniel Lacasse](https://github.com/Shad0w1nk) - Support GoogleTest for testing C++ binaries
* [Lóránt Pintér](https://github.com/lptr), [Daniel Vigovszky](https://github.com/vigoo) and [Mark Vujevits](https://github.com/vujevits) - implement dependency substitution for projects
* [Larry North](https://github.com/LarryNorth) - Build improvements.
* [Tobias Schulte](https://github.com/tschulte) - Documentation improvements
* [Stefan Oehme](https://github.com/oehme) - Addition of `Project.copy(Action)` and `Project.copySpec(Action)`

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
