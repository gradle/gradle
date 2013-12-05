## New and noteworthy

Here are the new features introduced in this Gradle release.

### Better support for building native binaries (i)

#### Project-wide definition of build types, platforms and flavors

#### Visual Studio plugin

#### Select the platforms, build types and flavors that a component should target

It is now possible to specify a global set of build types, platforms and flavors and then specifically choose which of
these should apply for a particular component. This makes it easier to have a single plugin that adds support for a
platform or build type, and have the build script use this if required.

- `buildTypes` is now `model.buildTypes`
- `targetPlatforms` is now `model.platforms`
- `executable.flavors` or `library.flavors` is now `model.flavors`
- Elements in these containers must be added with the `create(name)` method


    model {
        platforms {
            create('x86') {
                ... config
            }
        }
        buildTypes {
            create('debug')
        }
        flavors {
            create('my-flavor')
        }
    }


#### Improved support for project dependencies

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

### Changes to native binary support

- Moved definitions of `buildTypes`, `targetPlatforms` and `flavors` into model block (see above)

### A requested dependency returns different types of selectors

The method `DependencyResult.getRequested()` method was changed to return an implementation of type `ComponentSelector`. This change to the API has to be taken into account
when writing a `Spec` for the `DependencyInsightReportTask`. Here's an example for such a use case:

    task insight(type: DependencyInsightReportTask) {
        setDependencySpec { it.requested instanceof ModuleComponentSelector && it.requested.module == 'leaf2' }
    }

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
