## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Play Framework Support (i)

Gradle now supports building [Play](https://www.playframework.com/) applications for Play versions 2.2.x and 2.3.x.  This support allows users
to model, build, test, run and package Play applications.  It includes support for:

* Compiling Scala and Java controllers, tests, and model classes
* Processing routes files
* Processing Twirl templates
* Compiling coffeescript assets
* Minifying javascript assets
* Running tests with JUnitRunner
* Running applications in development mode
* Staging and creating Play distribution packages

Future releases will add more features and versions of the Play Framework.

See the [User Guide](userguide/play_plugin.html) as well as the sample builds delivered with the Gradle distribution for more information on using the `play` plugin.

### Support for verifying Gradle wrapper distribution download against SHA-256 hash

It is now possible to verify the integrity of the Gradle distribution downloaded by the [Gradle wrapper](userguide/gradle_wrapper.html) against
a known SHA-256 hash.

To enable wrapper verification you need only specify a `distributionSha256Sum` property in your project's `gradle-wrapper.properties` file.

    distributionSha256Sum=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

Please see section [Verification of downloaded Gradle distributions](userguide/gradle_wrapper.html#sec:verification) of the User Guide for more information.

This feature was contributed by [Dominik Schürmann](https://github.com/dschuermann).

### Support for functionally testing Gradle plugins

This release brings the initial version of, the long awaited, official support for functionally testing Gradle plugins.
That is, being able to programmatically execute a contrived build leveraging a plugin under development as part of the plugin's build.
This functionality is being delivered as part of the new Gradle TestKit.

Many community members stepped up and filled the void created by the absence of a built in way to do this.
Notably, the folks at [Netflix](http://netflix.com/) contributed the very popular [Nebula Test](https://github.com/nebula-plugins/nebula-test) as part of their [Nebula Project](https://github.com/nebula-plugins).
The functionality provided by this and other similar projects will over time be rolled into the Gradle TestKit.

See the [new Gradle TestKit user guide chapter](userguide/test_kit.html) for more information.

### Rule based model configuration (i)

Gradle 2.5 brings significant enhancements to the visualization of Rule based model configuration. The additional detail included in the model report, along
with improvements to build errors, provides deep insight into what the model space looks like.

The information included on the model report such as: what created a rule, where a rule was created, which rules apply to an element, the order in which rules are applied and the value of a particular
model element are instrumental in understanding the state and relationships of the model space.

The improvements to the error report generated, when a rule's inputs and/or subjects fail to bind, make it easier for build authors to pinpoint the root cause of build configuration errors. These improvements
present the build author with vital debugging information such as:
    * Which inputs or subjects could not be bound.
    * Where, in the build script, the missing rule dependency was identified.
    * The model path to the model element with the failure.
    * Which method parameter, on a method rule, could not be bound.
    * Suggestions as to which inputs/subjects could be used to successfully bind.

See the [Rule based model configuration user guide chapter](userguide/new_model.html) for more information.

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


## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Steve Ebersole](https://github.com/sebersole) - Support for passing arbitrary arguments to FindBugs tasks
* [Malte Finsterwalder](https://github.com/finsterwalder) - Fixed resolving of references to `${parent.artifactId}` in POM files (GRADLE-3299)
* [Roy Kachouh](https://github.com/roykachouh) - Fix for Application plugin script generation in projects with alphanumeric names
* [Sebastian Schuberth](https://github.com/sschuberth) - Documentation improvements
* [Andrew Shu](https://github.com/talklittle) - Documentation improvements
* [Dominik Schürmann](https://github.com/dschuermann) - Support for verifying Gradle wrapper distribution download against SHA-256 hash
* [Amit Portnoy](https://github.com/amitport) - Minor fix in LifecycleBasePlugin
* [Jordan Jennings](https://github.com/jordanjennings) - Documentation improvements
* [Zoltán Kurucz](https://github.com/qzole) - Documentation improvements
* [Yu Lu](https://github.com/yulucodebase) - Logging improvements
* [Ben Blank](https://github.com/benblank) - Allow CopySpec.filter() to remove lines
* [Harald Schmitt](https://github.com/surfing) - Fixed mixup in credentials design spec

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
