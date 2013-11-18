
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

## Options:

    1. Change the semantics of test.include so that it supports syntax like {ant-file-pattern}#{testMethodName}, e.g. **/FooTest#someMethod
Test implements PatternFilterable, which declares the exact behavior of include / exclude. In order to implement this option, we would need to introduce a mildly breaking change and make Test no longer extend PatternFilterable. It is kind of a mildly breaking because we would keep all the existing public methods in the Test type. If users extends Test task, they would probably need to recompile. For command line, we introduce --include option for the Test task. Pros: api remains small, Cons: breaking change.

    2. Introduce new dsl (various ideas all lumped together):

    test {
      selection {
        //using 'include' wording here could make it confusing with test.include
        select "**/Foo*#someOtherMethod", "**/Foo*#someMethod"
        selections = []

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

    Then add consistent commandline support, e.g.

    gradle test --select **/Foo.java#someTest

## command line interface

It would be good to consider the command line interface when designing the dsl because they need to be consistent.
The convenient command line should support:

	- selecting class + method in one option
	- allow wildcards for classes (wildcards for methods are not practical to implement)
	- support both, file separators '/' or fqn separators '.'

Examples:

    --select com/bar/**/Foo.java#someTest
    --select Foo#someTest
    --select com.foo.bar.Foo#otherTest

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