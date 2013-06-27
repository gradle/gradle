
This spec decribes an approach to making test execution in Gradle more flexible

# Use cases

- I have a set of functional tests.
- I have a set of fast and slow tests.
- I need to run my functional tests against multiple different environments.
- I have unit tests for a C++ library, and I need to build and execute the unit tests for each variant of the library.
- I have a set of performance tests.
- My unit and functional tests have different dependencies.
- My functional tests do not use the production classes directly.
- My functional tests require some environment setup and tear down.
- I have a set of test fixtures which are shared by different groups of tests.
- I use several different test frameworks in my unit tests.

# Implementation plan

Introduce the concept of a _test suite_ and add a container of test suites to the project. A new `testing` plugin
will make these available:

    tests {
        unit { ... }
        functional { ... }
    }

A test suite takes a test binary as input (where _binary_ is as defined in the [jvm languages](building-multiple-outputs-from-jvm-languages.md)
and [native languages](continuous-delivery-for-c-plus-plus.md) specs). This binary includes the candidate tests that form
part of the suite, subject to filtering. A binary defines its runtime dependencies and these are used to determine how to
run the tests and which toolkits are to be used.

A test suite may also define test toolkit-specific configuration, such as JUnit categories to include or exclude, and
environment specific configuration, such as system properties to define:

    tests {
        unit {
            junit {
                includeCategory 'Fast'
            }
            testng {
                excludeGroup 'Slow'
            }
            systemProperty 'someProp', 'some-value'
        }
    }

A test suite has an associated task that executes the test suite. It may also have additional reporting tasks associated
with it.

TBD - strongly type test suites for JVM and native environments.

TBD - It's probably worth splitting test suite into 2 things: the logical test suite, and the concrete executions of that
suite for each environment and variant.

Retrofit the `java` plugin to define a `unit` test suite that uses a `testClasses` binary build from the `test` source set.

Add some "by-convention" plugins (say one for JVM and one for native environments) that define a number of conventions:

- Define a `testFixtures` binary built from an associated `testFixtures` source set in `src/test-fixtures/$lang`.
- When a test suite `foo` is added, define a `fooTest` binary built from an associated `fooTest` source set with source in `src/foo-test/$lang`.
- Add the `testFixtures` binary as a dependency of each test binary.


# Open issues
