# Testing

Below are some of the basic principles for writing tests for Gradle and [contribute to it](../CONTRIBUTING.md), however, this guide is not exhaustive.

Our tests are written in [Spock](https://spockframework.org/spock/docs/).
You can find its exact version [here](../packaging/distributions-dependencies/build.gradle.kts).

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

