## Feature: Test Execution

## Story: Improved feedback to user on test failure

Fix some feedback issues:

- Currently, when tests fail, the console displays 'BUILD SUCCESSFUL'. The client does receive an exception with the test failure details.
- Currently, when no tests match the request, the console displays 'BUILD SUCCESSFUL'. The client does receive an exception with the failure details.

## Story Cache result of test detection in Test task

Allow caching of test detection logic in Test task. This is a prerequisite of the next story
"Run only those test tasks that match the test execution request"

### Implementation
* Add a CachingTestFrameworkDetector to wrap testFrameworkDetectors.
    * caches detected test classes

* Invalidate test detection cache when task input has changed.
    * declare `CacheInvalidator` that checks for changed inputs

### Test coverage

* runs all tests when executing test task multiple times with same input.
    (e.g.: gradle test; gradle cleanTest test;
* detects new added test classes
* detects new tests when super class not in testclassesDir but in classpath
* detects all files when test framework changed
* add coverage for above for Junit and TestNG

### Open questions

* can input cache from incremental task calculation be reused?

## Story: Run only those test tasks that match the test execution request

Running all `Test` tasks with a filter has a serious downside: all the dependencies and finalizers for these tasks are run, even when not required.
For example, when a functional test suite requires some service to be provisioned and a data store of some kind to be created, this work will be on
every invocation of the test launcher, say when running unit tests, even when not required.

Instead, detect which `Test` task instances to run based on their inputs.

### Implementation

* Run tasks that build the test task(s) input classpaths.
* Apply test detection.
* Determine which `Test` tasks to run
* Run these tasks and their dependencies and finalizers.
* Do not run `Test` tasks that do no match, nor their dependencies or finalizers.
* Calculate Test#testClassesDir / Test.classpath to find all tasks of type `org.gradle.api.tasks.testing.Test` containing matching pattern/tests
* Execute matching Test tasks only

## Story: deal with non found test requests

The test launcher api doesn’t complain about test requests that don’t match anything if at least one test request matches something
(eg. if I request a class that exists and a class that does not exist, we ignore the class that does not exist)

## Story: better handling of non matching tests

When no matching tests have been requested, console output says ‘build successful’
even though a ‘no matching tests found’ exception is thrown

### Implementation
- use the
The test launcher api doesn’t complain about test requests that don’t match anything if at least one test request matches something
(eg. if I request a class that exists and a class that does not exist, we ignore the class that does not exist)

## Story: Rerun a failed JUnit test that uses a custom test runner

For example, a Spock test with `@Unroll`, or a Gradle cross-version test. In general, there is not a one-to-one mapping between test
method and test execution. Fix the test descriptors to honour this contract.

## Story: Add ability to launch tests in debug mode

Need to allow a debug port to be specified, as hard-coded port 5005 can conflict with IDEA.
Debugging is enabled by enabling debug connector via socket;

### Implementation

- add `debugPort` property to `JvmOptions` and `JavaForkOptions`
- default value for `debugPort` should be 5005
- add parser for integer based options to `org.gradle.api.internal.tasks.options.OptionNotationParserFactory`
- assign `@Option` to `Test#setDebugPort`
- if `Test.debug` is enabled ensure only a single test worker is used
- Add `TestLauncher#withDebugEnabled()` and `TestLauncher#withDebugEnabled(int port)`

### Test coverage

- debug options can be set via `JvmOptions#allJvmArgs` and proper port is picked up
- can invoke test task with debug enabled by
    - configuring debug port in build script
    - declaring debug port as command line option (--debug-port)
- connecting to test process works for tests launched via normal gradle build
- debugging tests declared for testlauncher can be debugged with default port `5005` for tests launched via tooling api `TestLauncher#withDebugEnabled()`
- debugging tests declared for testlauncher can be debugged with custom port for tests launched via tooling api `TestLauncher#withDebugEnabled(customPort)`
- can connect to test process under debug (create simple jdi based fixture)
    - have line information available.
    - define breakpoint
    - step over code
    - resume process
- works with configured `Test#forkEvery > 0`
- works with configured `Test#maxParallelForks > 1`
- works with configured `Test#maxParallelForks > 1` & `Test#forkEvery > 0`

### Open questions

- How to deal with maxParallelForks / forksEvery? feels like it makes sense to restrict it to maxParallelForks=1 & forkEvery=0 if debug is enabled.

## Story: Allow specification of tests from candidate invocations of a given test

A test class or method can run more than once. For example, the test class might be included by several different `Test` tasks,
or it might be a method on a superclass with several different subclasses, or it might have a test runner that defines several invocations for the test.
It would be nice to present users with this set of invocations and allow selection of one, some or all of them.

TBD

# Backlog

- Allow specification of tests to run via package, patterns, TestDiscriptor inclusion/exclusion
- Improve behaviour when task no longer exists or no longer contains the requested test, but the test still exists (eg it has moved)
