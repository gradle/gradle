# Gradle 3.0

Gradle 3.0 is the next major Gradle release that offers the opportunity to make breaking changes to the public interface of Gradle. This document captures a laundry
list of ideas to consider before shipping Gradle 3.0.

Note: for the change listed below, the old behaviour or feature to be removed should be deprecated in a Gradle 2.x release. Similarly for changes to behaviour.

# Stories

## Remove deprecated elements (DONE)

- Remove deprecated methods from:
    - Specs (Done)
    - StartParameter (Done)
    - ArtifactRepository, plus special case handling to 'lock' this when added to a repository container (Done)
    - AbstractLibrary (Done)
    - EclipseClasspath (Done)
    - org.gradle.plugins.ide.eclipse.model.ProjectDependency (Done)
    - org.gradle.tooling.model.Task (Done)
    - PrefixHeaderFileGenerateTask (Done)
- Remove deprecated:
    - `--parallel-threads` command-line option (Done)
    - old wrapper properties from `WrapperExecutor`, remove generation of these properties from `Wrapper` task (Done)
- Move `Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER` from public API. (Done)
- Move internal types `org.gradle.logging.StandardOutputCapture` and `org.gradle.logging.LoggingManagerInternal` into an internal package (deprecation was deferred to 4.0 as per [issue #83](https://github.com/gradle/core-issues/issues/83)).
- Merge `Module` and `ModuleInternal`, now that `Module` is internal (Done)
- Internal `has()`, `get()` and `set()` dynamic methods exposed by `ExtraPropertiesDynamicObjectAdapter` (Done)
- Constructor on `DefaultSourceDirectorySet` - not for 3.0, deprecation only happened in 2.14 and it is widely used

## Change minimum version for running Gradle to Java 7 (DONE)

No longer support running Gradle, the wrapper or the Tooling api client on Java 6. Instead, we'd support Java source compilation and test execution on Java 6 and later, as we do for Java 1.5 now.

- Allow Java 6 to be used for findbugs execution? - No
- Update project target versions 
- Remove customisations for IDEA project generation.
- Remove Java 7 checks, eg from continuous build.
- Remove `TestPrecondition.JDK6` and similar.
- Remove deprecation warning disable from `GradleBuildComparison.createProjectConnection()`
- Remove deprecation warning disable from `SamplesToolingApiIntegrationTest` and `SamplesCompositeBuildIntegrationTest`
- Remove special case handling of deprecation message from test fixtures.
- Document minimum version in user guide
- Verify wrapper can run older versions on java 5 or java 6

### Test coverage

- Warning when running Gradle entry point on Java 6:
    - `gradle`
    - `gradle --daemon`
    - `gradlew`
    - `GradleConnector`
    - `GradleRunner`
    - old `gradlew`
    - old `GradleConnector`
- Warning when running build on Java 6 with entry point running on Java 7+
    - `gradle`
    - `gradle --daemon`
    - `gradlew`
    - `GradleConnector`
    - `GradleRunner`
    - old `gradlew`
    - old `GradleConnector`

## Change minimum version for building and testing Java source to Java 6 (DONE)

Change cross-compilation and test execution to require Java 6 or later.
Building against Java 6 requires that the compiler daemon and test execution infrastructure still support Java 6.

- Document minimum version in user guide
- Add samples and documentation to show how to compile, test and run using a different Java version.
- Clean up `DefaultClassLoaderFactory`. - Not possible, the workaround is still necessary for Java 6
- Change `InetAddressFactory` so that it no longer uses reflection to inspect `NetworkInterface`.
- Replace usages of `guava-jdk5`. - Not for Gradle 3.0

### Test coverage

- Warning when running tests on Java 5.
- Can cross-compile and test for Java 6 with no warning

## Change minimum Gradle version supported by the tooling API client to Gradle 1.2 (DONE)

Tooling api client no longer executes builds for Gradle versions older than 1.2. Tooling api client 2.x supports Gradle 1.0-m8 and later.

- Deprecate support in build comparison and `GradleRunner` for versions older than 1.2
- Update 'unsupported version' error message to mention 1.2 rather than 1.0-m8.
- Remove `InternalConnectionBackedConsumerConnection` and `CompatibleIntrospector`.
- Update documentation for tooling api methods added from 1.0-m8 to 1.2, to indicate that these are now supported for all versions.
- Remove special case handling of deprecation message from test fixtures.

### Test coverage

- Warning when running a build or fetching model for Gradle 1.1 or earlier
    - `ProjectConnection`
    - `GradleConnection`
    - `GradleRunner`

## Change minimum tooling API client version supported to Gradle 2.0 (DONE)

- Update 'unsupported version' error message to mention 2.0 rather than 1.2

### Test coverage

- Warning when running a build, a build action or fetching model using tapi 1.12 or earlier
    - `ProjectConnection`

## Remove Sonar plugins (DONE)

Remove the Sonar plugins

## Remove support for TestNG source annotations (DONE)

TestNG dropped support for this in 5.12, in early 2010. Supporting these old style annotations means we need to attach the test source files as an input to the `Test` task, which means there's an up-to-date check cost for this.

- `Test.testSourceDirs`
- Methods on `TestNGOptions`
- Remove reflective code from `TestNGTestClassProcessor` and properties from `TestNGSpec`
- Remove test coverage and samples

## Fix the delegate and parameter for named container configuration closure (DONE)

Currently, a `ConfigureDelegate` instances is made visible to the closure as its parameter (eg `it`) and its delegate. This is not the case for other configuration closures.

## Test output directories (DONE)

The current defaults for the outputs of tasks of type `Test` conflict with each other:

* Change the default result and report directory for the `Test` type to include the task's name, so that the default
  does not conflict with the default for any other `Test` task.
* Change the default TestNG output directory.