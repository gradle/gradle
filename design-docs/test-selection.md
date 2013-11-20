
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
- I want to measure code coverage for my integration tests.
- I want int test code coverage to be included in sonarRunner analysis.
- legacy integration tests are slow, I need a way to execute a single test method, from the command line.

# Introduce _test suite_

## Implementation plan

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

## Open issues

# single test method execution

## Use case

Legacy integration tests are slow, @ need a way to execute a single test method, from command line.

## New dsl brainstorm

    test {
        selection {
            //using 'include' wording here could make it confusing with test.include
            only "**/Foo*#someOtherMethod", "**/Foo*#someMethod"
            only = []

            //in/out
            in "**/Foo*#someOtherMethod", "**/Foo*#someMethod"
            out "**/Foo*#someOtherMethod", "**/Foo*#someMethod"

            //if we deprecate test.include, we could do
            include "**/Foo*#someOtherMethod", "**/Foo*#someMethod"
            includes = []

            //keep method selection separate
            includeMethod
            includeMethods = []

            include {
                method
                methods = []
            }

            //some other elements we could add in future
            exclude
            excludes = []

            unselect
            unselections = []

            include {
                descendantsOf 'com.foo.SomeBaseClass'
                annotatedWith 'com.foo.Slow'
                matching { descriptor, testClass ->
                    //...
                }
            }
        }
    }

Plus consistent commandline support, e.g.

gradle test --select **/Foo.java#someTest

## command line interface

It would be good to consider the command line interface when designing the dsl because they need to be consistent.
The convenient command line should support:

	- selecting class + method in one option
	- allow wildcards for classes (wildcards for methods are not practical to implement)

Examples:

    --select com.bar.**.Foo#someTest
    --select Foo#someTest
    --select com.foo.bar.Foo#otherTest

## Plan

    1. Add --only @Option, that maps to new dsl: test.selection.only
    1. Add --include @Option and deprecate test.single. The new option is equiv of test.include
    1. Add --exclude @Option, the option maps to test.exclude
    1. Add --debug (while we're in the area)

## Stories

### Add --only @Option, that maps to new dsl: test.selection.only

#### Test coverage:

    - happy path, allows running tests for single methods
    - when running the same test with different method, make sure it is not up-to-date
    - works with JUnit and TestNG
    - selected test(s) ignores completely any existing include configured in the build script but respects excludes
    - is correctly incremental (e.g. same VS different values passed with --only)
    - "Could not find matching test" error is emitted when no tests found for given --only
    - 2 different test tasks, both use the option with different tests included

### Add --include @Option to the test task, deprecate test.single property

#### Test coverage:

    - happy path
    - selected test ignores completely any existing include configured in the build script
    - is correctly incremental (e.g. same VS different values passed with --include)
    - "Could not find matching test" error is emitted when no tests found for given --include
    - 2 different test tasks, both use the option with different tests included
    - if combined with --only, --only wins and --include is ignored

### Add --exclude @Option to the test task:

#### Test coverage:

    - happy path
    - exclude from command line replaces any excludes declared in the build script
        (this behavior is consistent with --include)
    - is correctly incremental (e.g. same VS different values passed with the option)
    - "Could not find any tests" error is emitted when no tests found for given --exclude
        (consistently with --include)
    - --include and --exclude both working together together
        - include declares a dir, exclude declares a single test from this dir,
        the behavior should be consistent to how it works currently via DSL API (given both: includes and excludes configured in the build script)
    - if combined with --only, --only wins and --exclude is ignored

### Add --debug @Option to the test task

#### Test coverage:

    - smoke test, make sure the task is happy with the option and that debug property is set on the task

## The api I currently think on implementing as a first story, please give feedback:

    test {
      //good old include
      include '**/*SomeIntegrationTest'

      //new stuff
      selection {
        include {
          method 'method1', 'method2'
          methods = []
        }
      }
    }

Plus, a convenience method directly in the test task, so that all above could be inlined into:

    test {
      select '**/*SomeIntegrationTest#method1,method2'
      //or:
      select '**/*SomeIntegrationTest#method1', '**/*SomeIntegrationTest#method2'
    }

Then, command line support could be:

    gradle test --select **/*SomeIntegrationTest#method1,method2

I would also deprecate test.single property








