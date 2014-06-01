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

## Reset deprecation warnings

* Remove most calls to `DeprecationLogger.whileDisabled()`

## All Gradle scripts use UTF-8 encoding

* Change Gradle script parsing to assume UTF-8 encoding.
* Prefer character encoding specified by the server, if any.
* Update user guide to mention this.

## Upgrade to most recent Groovy 2.2.x

* Change the version of Groovy exposed via the Gradle API to most recent Groovy 2.2.x version.
* Add int test coverage for compilation and groovydoc for various permutations of Groovy versions and (`groovy` or `groovy-all`)

## Remove support for running Gradle on Java 5

In order to add support for Java 8, we will need to upgrade to Groovy 2.3.x, which does not support Java 5.
Would still be able to build for Java 5.

* Add cross-compilation int tests for Java 5 - 8.
    * Java project.
    * Compile and run tests using JUnit and TestNG.
* Document the JVM requirements in the user guide.
* Document how to build for Java 5 and include sample.
* Update CI builds to use newer Java versions.
* Entry points fail with reasonable error message when executed using Java 5.
    * Command-line.
    * Daemon, including single user daemon.
    * Wrapper.
    * Tooling API client.
    * Tooling API connection.
* Update CI builds to assert Java 5 is available.

## Add support for Java 8

* Change the version of Groovy exposed via the Gradle API to most recent Groovy 2.3.x version.
* Remove source exclusions for jdk6.
* Remove the special case logging from `LogbackLoggingConfigurer`.
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
