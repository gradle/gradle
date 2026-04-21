# Testing

Below are some of the basic principles for writing tests for Gradle and [contribute to it](../CONTRIBUTING.md), however, this guide is not exhaustive.

Our tests are written in [Spock](https://spockframework.org/spock/docs/).
You can find its exact version [here](../gradle/dependency-management/test.versions.toml).

We use a combination of unit tests and integration tests.
What we call integration tests are tests that run an entire Gradle build with specific build files and check its effects, so they are more like end-to-end tests.

See also our [Debugging guide](Debugging.md) that covers how to debug tests and Gradle builds.

## Take a look at existing tests

Before writing a new test, take a look at the existing tests that cover similar functionality.
They may provide useful examples of how to structure your test and what assertions to use.

## Create integration tests that exercise a Gradle build for the bug/feature

A good integration test will verify the effects of the build by examining the resulting external state, e.g., files that are produced by a build after some tasks are executed.
Users run Gradle builds to accomplish a goal, so a build should be written to produce output that is only produced when the desired effect is produced.

Avoid testing the internal state.
If you want that, maybe the integration test is not the right place to do it.
Verifying internal state (perhaps via mocks) is more appropriate for unit tests.

## Create unit tests if necessary

Unit tests are best for testing small, isolated pieces of code when there is a lot of variation in inputs.
Running all possible combinations of inputs in an integration test is not practical.
If it's hard to test a piece of code in isolation, it might be a sign that the code is too complex and should be refactored.

## KISS

Don't over-engineer your tests (e.g., by creating deeply-nested helpers), and don't be afraid to repeat yourself.
It should be easy to read and understand the test.

## Use helpers and traits

We have a bunch of helpers methods and traits that can be used to simplify the tests and make them more readable.
It's ok create a new helper or trait if you think it's a good abstraction, but please don't overdo it.
You can find the existing helpers [here](../testing/internal-integ-testing/src/main/groovy/org/gradle/).

## Use data-driven tables

[Data-driven tables](https://spockframework.org/spock/docs/2.3/data_driven_testing.html#data-tables) are a great way to test multiple scenarios in a single test.

## Don't use assertions in Gradle build scripts under test

Assertions in Gradle build scripts under test have plenty of caveats.
A test might pass just because the assert was not triggered.
Also, if it fails, you get a worse error and no comparison of expected vs actual value.

It's ok to print the data via stdout and verify the output in a test.
Some behaviors can be tested via build operations.

## Link tests that correspond to a bug to a GitHub issue

Use the `@spock.lang.Issue` annotation to link the test to a GitHub issue.
For example:

```groovy
    @Issue("https://github.com/gradle/gradle/issues/8840")
    def "can use exec in settings"() { ... }
``` 

# Cross Version Tests

Some tests in the Gradle codebase are executed with a wide range of supported Gradle versions.
There are two kinds of cross version tests, those that use the Tooling API (such a cross version test **must** extend `org.gradle.integtests.tooling.fixture.ToolingApiSpecification`) and those that don't.
Tooling API cross version tests are special because they need a special classloading setup for the executions using older Tooling API versions to later Gradle versions because of how Java class loading works.

## Required Project Setup

To add cross version tests to a project, an enabling plugin must be applied:

```kotlin
plugins {
    // ...
    id("gradlebuild.cross-version-tests")
}
```

The application of the `gradlebuild.cross-version-tests` plugin will create additional `SourceSet`s, `Configuration`s and many `Task`s, see below for details.

## Cross Version Test Project Infrastructure

### SourceSets

- `crossVersionTest` - SourceSet for your test code
- `crossVersionTestModels` - SourceSet for Models and <abbr title="Classes implementing org.gradle.tooling.BuildAction">BuildActions</abbr> you need for Tooling API cross version tests, compiled to a lower Java version with only the Tooling API available during compilation. Implement them in Java for the best compatibility with the wide range of supported Gradle versions.


### Configurations (trimmed)

> crossVersionTestCompileClasspath - Compile classpath for source set 'cross version test'.
> crossVersionTestImplementation - Implementation only dependencies for source set 'cross version test'.
> crossVersionTestLocalRepository - Declare a local repository required as input data for the tests (e.g. :tooling-api)
> crossVersionTestModelsCompileClasspath - Compile classpath for source set 'cross version test models'.
> crossVersionTestModelsImplementation - Implementation only dependencies for source set 'cross version test models'.
> crossVersionTestRuntimeOnly - Runtime only dependencies for source set 'cross version test'.

If your project needs cross version test models from another project, add a dependency like:

```kotlin
import gradlebuild.integrationtests.crossVersionTestModels

dependencies {
    crossVersionTestImplementation(crossVersionTestModels(projects.theOtherProject)) // or maybe crossVersionTestModelsImplementation
}
```

### Tasks (trimmed)

> allVersionsCrossVersionTest - Run cross-version tests against all released versions (latest patch release of each)
> platformTest - Run all unit, integration and cross-version (against latest release) tests in forking execution mode
> quickFeedbackCrossVersionTest - Run cross-version tests against a limited set of versions
> allVersionsCrossVersionTests - Runs the cross-version tests against all Gradle versions with 'forking' executer
> quickFeedbackCrossVersionTests - Runs the cross-version tests against a subset of selected Gradle versions with 'forking' executer for quick feedback
> gradle4.0.2CrossVersionTest - Runs the cross-version tests against Gradle 4.0.2
> gradle5.0CrossVersionTest - Runs the cross-version tests against Gradle 5.0
> gradle8.14.4CrossVersionTest - Runs the cross-version tests against Gradle 8.14.4
> gradle9.4.1CrossVersionTest - Runs the cross-version tests against Gradle 9.4.1

## Cross Version Test Execution

To execute cross version tests for a specific Gradle version, use the appropriately named task, example for the `tooling-api` project:

```shell
~> ./gradlew :tooling-api:gradle8.14.4CrossVersionTest -PtestJavaVersion=21 --tests '*Spec*' # filter for some test class name to speed up your feedback loop
```

**Note:** Different Gradle versions may not support running with the specified Java version (21 in the example `-PtestJavaVersion=21`) and in that case,
the test infrastructure will use the first lower Java version compatible with the Gradle version to be tested.
The lowest version accepted is the lowest version supported by the Gradle daemon of this version.

## Cross Version Test Pitfalls

### Executing with Kotlin scripts on Gradle Version prior to 5.0.

This will result in an invocation as if no script is present and you'll be puzzled why nothing you wrote in the script is happening.
The reason is that Kotlin DSL was introduced in Gradle 5.0. Older Gradle versions simply ignore Kotlin scripts.
The solution is to use a Groovy script since it works on all Gradle Versions.

### Tooling API Cross Version Test `NoClassDefFoundError`

When Tooling API cross version tests execute test cases where an older Tooling API is used to execute the test (Example: when the Tooling API from Gradle 8.14.4 is executing a test using Gradle 9.0.0), test classes are **reloaded** in a more restrictive ClassLoader (See `org.gradle.integtests.tooling.fixture.ToolingApiClassLoaderProvider`) than the one set up by Gradle.
If the class that can't be loaded is a Model class or a BuildAction, move the class to the `crossVersionTestModel` source set of the project.
If the class is from the distribution, it may be an option to allow the class to be loaded by extending the config of the `FilteringClassLoader` created in the `ToolingApiClassLoaderProvider`.
