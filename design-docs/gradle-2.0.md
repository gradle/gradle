# Gradle 2.0

Gradle 2.0 is the next major Gradle release that offers the opportunity to make breaking changes to the public interface of Gradle. This document captures a laundry
list of ideas to consider before shipping Gradle 2.0.

Note: for the change listed below, the old behaviour or feature to be removed should be deprecated in a Gradle 1.x release, probably no later than Gradle 1.9. Similarly
for changes to behaviour.

# Planned for 2.0

The following stories are to be included in Gradle 2.0.

## Un-deprecate using the packaging declared in a Maven POM to probe for the module artifacts

Leave this behaviour in until the mechanisms to better infer the artifacts for a module have been implemented.

## Remove all features deprecated as at Gradle 1.12

In the Gradle 2.0-rc-1 release, remove all features that are deprecated as at Gradle 1.12 or earlier:

* Search for usages of `DeprecationLogger`, `@Deprecated`, `@deprecated` and remove the associated feature.
* Review usages of `DeprecationLogger.whileDisabled()`.
* Remove `JavaPluginGoodBehaviourTest#changing debug flag does not produce deprecation warning`

## Replace deprecation warnings with errors

* Convert deprecated behaviours with errors.

## Remove Ivy types from the Gradle repository API

These types expose the implementation details of dependency management and force a certain implementation on Gradle. Removing these types from the API
allows us to implement new features and remove some internal complexity.

* Remove methods from `ArtifactRepositoryContainer` and `RepositoryHandler` that accept an Ivy `DependencyResolver` as parameter.
* Remove methods from `ArtifactRepositoryContainer` that return `DependencyResolver`.
* Remove `RepositoryHandler.mavenRepo()`.
* Change the `AbstractMavenResolver` so that it no longer extends `DependencyResolver`.
* Change the `FlatDirRepository` implementation so that it no longer uses a `DependencyResolver` implementation.
* Remove Ivy packages from the Gradle API filter.
* Remove Ivy as a dependency of core.
* Remove Ivy version from the output of `gradle -v`.
* Remove loopback resolver, ModuleVersionRepository -> Ivy adapter.
* Remove properties from `ExternalResourceResolver` and subclasses.
* Remove `ModuleComponentRepository.canListModuleVersions()`.
* Fix `ExternalResourceResolver.getMetaDataArtifactName()` so that it is not nullable.

## Remove tooling API support for Gradle 1.1 clients and earlier (DONE)

Gradle 1.2 was released on 12th sept 2012. This change means that tooling more than roughly 18 months old as of the Gradle 2.0 release
will not be able to invoke Gradle 2.0 or later.

* Change the implementation of methods on `ConnectionVersion4` and `InternalConnection` to fail with a decent error message.
* The model implementations no longer need to implement `ProjectVersion3` of the protocol interfaces.
* Change test suite to default to tooling API versions >= 1.2.
* Add integration test coverage that tooling API versions <1.2 fail with a reasonable error message, when running build or fetching model.

## Remove tooling API support for Gradle providers 1.0-milestone-7 and earlier (DONE)

Gradle 1.0-milestone-8 was release on 14th feb 2012. This change means that tooling will not be able to run builds using Gradle versions more than
approximately 2 years old as of the Gradle 2.0 release.

* Consumer fails with a decent error message instead of falling back to the methods on `ConnectionVersion4`.
* Add support for fetching partial `BuildEnvironment` model for unsupported versions.
* Change the test suite to default to target Gradle version >= 1.0-milestone-8
* Add integration test coverage that running build with Gradle version < 1.0-milestone-8 fails with a reasonable error message, when running build or fetching model.
* Add integration test coverage that can fetch a partial `BuildEnvironment` model for Gradle version < 1.0-milestone-8.

## Reset deprecation warnings

* Remove most calls to `DeprecationLogger.whileDisabled()`

## All Gradle scripts use UTF-8 encoding

* Change Gradle script parsing to assume UTF-8 encoding.
* Prefer character encoding specified by the server, if any.
* Update user guide to mention this.

## Upgrade to most recent Groovy 2.2.x

* Change the version of Groovy exposed via the Gradle API to most recent Groovy 2.2.x version.
* Change to use `groovy` instead of `groovy-all`.
    * Change Groovy runtime detector to deal with this change.
* Add int test coverage for building and groovydoc for permutations of Groovy versions and (`groovy` or `groovy-all`)

## Remove support for running Gradle on Java 5

In order to add support for Java 8, we will need to upgrade to Groovy 2.3, which does not support Java 5.
Would still be able to build for Java 5.

* Add cross-compilation int tests for Java 5 - 8.
* Document how to build for Java 5.
* Compile wrapper, launcher and tooling API connection entry points separately for Java 5.
* Update CI builds to use newer Java versions.
* Entry points complain when executed using Java 5.
* Drop support for running with Java 5.
* Clean up `DefaultClassLoaderFactory`.

## Add support for Java 8

* Change the version of Groovy exposed via the Gradle API to most recent Groovy 2.3.x version.
* Remove source exclusions for jdk6.
* Change `InetAddressFactory` so that it no longer uses reflection to inspect `NetworkInterface`.
* Remove the special case logging from `LogbackLoggingConfigurer`.
* Replace usages of `guava-jdk5`.
* Clean up usages of `TestPrecondition.JDK5` and related preconditions.
* Add warning when using Java version > 8 to inform the user that the Java version may not be supported.

## Archive tasks + base plugin

* Move defaults for output directory and other attributes from the base plugin to an implicitly applied plugin, so that they are applied to all instances.
* Use `${task.name}.${task.extension}` as the default archive name, so that the default does not conflict with the default for any other archive task.

## Test output directories

The current defaults for the outputs of tasks of type `Test` conflict with each other:

* Change the default result and report directory for the `Test` type to include the task's name, so that the default
  does not conflict with the default for any other `Test` task.
* Change the default TestNG output directory.
