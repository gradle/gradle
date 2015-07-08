## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Play Framework Support (i)

Gradle now supports building [Play](https://www.playframework.com/) applications for Play versions 2.2.x and 2.3.x.  This is the initial iteration of the plugin.

Future releases will increase the support for other features and versions of the Play Framework.  The Gradle distribution comes with several sample builds using Play for you to try out.

See the new User Guide section about using the [`play` plugin](userguide/play_plugin.html).

### Support for verifying Gradle wrapper distribution download against SHA-256 hash

It is now possible to verify the integrity of the Gradle distribution downloaded by the [Gradle wrapper](userguide/gradle_wrapper.html) against
a known SHA-256 hash.

To enable wrapper verification you need only specify a `distributionSha256Sum` property in your project's `gradle-wrapper.properties` file.

    distributionSha256Sum=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

Please see section [Verification of downloaded Gradle distributions](userguide/gradle_wrapper.html#sec:verification) of the User Guide for more information.

This feature was contributed by [Dominik Schürmann](https://github.com/dschuermann).

### Gradle test-kit

One of the features the community has been yearning for a long time is the ability to write and execute tests for Gradle build logic. The Gradle team is excited to have started this work with this release.
Gradle now supports a test-kit for writing functional tests for build scripts agnostic of the testing framework. The execution of the tests is performed by the Tooling API. This functionality is a
tremendous step toward the capability of rigorously testing custom extensions to Gradle, a use case often encountered by build authors and Open Source plugin developers. Future releases will increase
the support for other features.

See the new User Guide section about using the [test-kit](userguide/test_kit.html).

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

### Deprecation of methods on (incubating) software model

* `BinarySpec.getSource()` has been deprecated and replaced with `BinarySpec.getInputs()`.
* `ComponentSpec.getSource()` has been deprecated and replaced with `ComponentSpec.getSources()`.

These incubating methods will be removed in the next minor version.

## Potential breaking changes

* Removed `FunctionalSourceSet.copy()`

### Changes to source set handling of binaries

Binaries now distinguish between source sets that are specific to them (owned source set) and external source sets that are also required to build them.

* `BinarySpec.sources()` now takes an `Action` that operates on a `ModelMap<LanguageSourceSet>` instead of a `PolymorphicDomainObjectContainer`. Source sets defined here are specific to the binary.
* Added `BinarySpec.getSources()` that returns only the sources specific to the binary.
    * Note: this method shadows access to `ComponentSpec.getSources()` when used in a nested `binaries` block.
* Added `BinarySpec.getInputs()` that contains all the source sets needed to build the binary, including the ones specific to the binary and external source sets (e.g. inherited from the binary's parent component).
* Removed `BinarySpec.source(Object)`: to add an existing sourceSet to a binary, use `BinarySpec.getInputs().add()`.
* `@Managed` models are no longer permitted to have setter methods for members of type `ManagedSet`.

### Rule based model configuration
* The model report displays each rule that has been executed against a given model element.
* The naming of rule source (`org.gradle.model.RuleSource`) method rules, appearing on the model report and in build exceptions, has changed as follows:
    - package names are omitted
    - Method parameters are excluded
      - Model report comparison
        - Before
        ```
                  | Rules:  |
                     ⤷ PersonRules#person(Person)
                     ⤷ PersonRules#setFirstName(Person)
                     ⤷ org.acme.SomeClass#method(ParameterType)
                     ⤷ model.person
        ```
        - After
        ```
                  | Rules:
                     ⤷ PersonRules#person
                     ⤷ PersonRules#setFirstName
                     ⤷ SomeClass#method
                     ⤷ model.person
        ```
      - Build exception comparison
        - Before:
        ```
        The following model rules are unbound:
          org.gradle.model.ManagedNodeBackedModelMapTest$ElementRules#connectElementToInput(org.gradle.model.ManagedNodeBackedModelMapTest$Bean, java.lang.String)
        ```

        - After:
        ```
        The following model rules are unbound:
          ManagedNodeBackedModelMapTest$ElementRules#connectElementToInput
        ```


## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Steve Ebersole](https://github.com/sebersole) - Support for passing arbitrary arguments to FindBugs tasks
* [Malte Finsterwalder](https://github.com/finsterwalder) - Fixed resolving of references to `${parent.artifactId}` in POM files (GRADLE-3299)
* [Roy Kachouh](https://github.com/roykachouh) - Fix for Application plugin script generation in projects with alphanumeric names
* [Sebastian Schuberth](https://github.com/sschuberth) - Documentation improvements
* [Andrew Shu](https://github.com/talklittle) - Documentation improvements
* [Dominik Schürmann](https://github.com/dschuermann) - Support for verifying Gradle wrapper distribution download against SHA-256 hash
* [Amit Portnoy](https://github.com/amitport) - Minor fix in LifecycleBasePlugin
<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
