# Testing

Our tests are written in [Spock](https://spockframework.org/spock/docs/).
We use a combination of unit tests and integration tests.

Below are some guidelines for writing tests.

## Create integration tests that exercise a Gradle build for the bug/feature

Test for the effects of the build, external state, e.g., changed files.
Users run Gradle builds to get some effect, so we should test that the build produces the expected effect.

Avoid testing the internal state.
If you want that, maybe the integration test is not the right place to do it.

## Create unit tests if necessary

Unit tests are best for testing small, isolated pieces of code when there is a lot of variation in inputs.
Running all possible combinations of inputs in an integration test is not practical.
If it's hard to test a piece of code in isolation, it might be a sign that the code is too complex and should be refactored.

## KISS

Don't over-engineer your tests, and don't be afraid to repeat yourself.
It should be easy to read and understand the test.

## Use helpers and traits

We have a bunch of helpers methods and traits that can be used to simplify the tests and make them more readable.
It's ok create a new helper or trait if you think it's a good abstraction, but please don't overdo it.

## Use data-driven tables

[Data-driven tables](https://spockframework.org/spock/docs/2.3/data_driven_testing.html#data-tables) are a great way to test multiple scenarios in a single test.

## Don't use assertions in Gradle build scripts under test

Assertions in Gradle build scripts under test have plenty of caveats.
A test might pass just because the assert was not triggered.
Also, if it fails, you get a worse error and no comparison of expected vs actual value.

It's ok to print the data in the stdout and check it in the test.
Some behaviors can be tested via build operations.

## Link tests that correspond to a bug to a GitHub issue

Use the `@spock.lang.Issue` annotation to link the test to a GitHub issue.
For example:

```groovy
    @Issue("https://github.com/gradle/gradle/issues/8840")
    def "can use exec in settings"() { 
    ...
``` 

