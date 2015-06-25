## Feature: Test Execution

## Story: Add ability to launch tests

### API proposal

* new method `newTestLauncher()` to ProjectConnection create test specific long running operation. returns `TestLauncher`
* `TestLauncher` interface contains all information about which tests should be executed.
* can configure `TestLauncher` via

The API:

    interface ProjectConnection {
        TestLauncher newTestLauncher();
    }

    interface TestLauncher extends LongRunningOperation {
	    TestLauncher withJvmTestClasses(String...);
	    TestLauncher withJvmTestMethods(String testClass, String... methods);

	    void run(); // Run synchronously
        void run(ResultHandler<? super Void> handler); // Start asynchronously
    }

From a client this API can be used like:

		ProjectConnection connection = GradleConnector.newConnector()
		   	.forProjectDirectory(new File("someFolder"))
		   	.connect();

		try {
		   //run tests
		   connection.newTestLauncher()
			 .withJvmTestClasses('example.MyTest')
			 .withJvmTestMethods('example.MyTest2', "testMethod1", "testMethod2")
			 .addProgressListener(new MyTestListener(), EnumSet.of(OperationType.TEST))
		     .setStandardOutput(System.out)
		     .run();
		} finally {
		   connection.close();
	}

### Implementation

* Introduce new `TestLauncher`
* Add factory method `ProjectConnection#newTestRunner()`
* Add a new protocol interface with a method that will accept a test execution request. The provider connection will implement this interface.
* Add a new `BuildModelAction` subtype to represent a test execution request.
* Add a new `BuildActionRunner` subtype to handle this request.
* Extract a decorator out of the current `BuildActionRunner` implementations to take care of wiring up listeners to send events back to build client.
* Add internal filter interfaces to allow test class and test method filters to be applied. Do not use patterns.
* Run all `Test` tasks with filters applied.

### Test Coverage

* can execute
	* single JVM test class
	* multiple specific JVM test classes
	* single test method of JVM test class
	* multiple test methods of JVM test class
* handles more than one test task
	* class included in multiple test tasks is executed multiple times, once for each test task
	* class included in multiple projects is executed multiple times, once for each test task
	* request class A and class B, where class A is included in task A, class B is included in task B
	* request class A, where class A is included in task A and not included in task B
	* when configure-on-demand is being used with a multi-project build
* test will not execute when test task is up-to-date
* tooling api operation fails with meaningful error message when no matching tests can be found
* build should not fail if filter matches a single test task
* expected test progress events are received in each case
* reasonable error message when target Gradle version does not support test execution
* does something reasonable when continuous build is used.

## Story: Run only those test tasks that match the test execution request

Running all `Test` tasks with a filter has a serious downside: all the dependencies and finalizers for these tasks are run, even when not required.
For example, when a functional test suite requires some service to be provisioned and a data store of some kind to be created, this work will be on
every invocation of the test launcher, say when running unit tests, even when not required.

### Implementation

* calculate Test#testClassesDir / Test.classpath to find all tasks of type `org.gradle.api.tasks.testing.Test` containing matching pattern/tests
* execute matching Test tasks only

## Story: Rerun a failed test

Add a method to `TestLauncher` to allow a `TestOperationDescriptor` to be provided to identify a particular test to run:

    interface TestLauncher {
        TestLauncher withTests(TestOperationDescriptor... testDescriptors);
    }

### Implementation

- Given a `TestOperationDescriptor`, it is possible to calculate exactly which test task to run.

### Test cases

- only the target test task is executed.
	- class is included in multiple test tasks.
- build fails when the target test no longer exists.
- does something reasonable when the target test task no longer exists, but the test still exists.
- does something reasonable when the target test is no longer part of the target test task.

## Story: Rerun a failed JUnit test that uses a custom test runner

For example, a Spock test with `@Unroll`, or a Gradle cross-version test. In general, there is not a one-to-one mapping between test
method and test execution. Fix the test descriptors to honour this contract.

## Story: Allow forced execution of up-to-date test tasks

### Implementation

* add flag to TestLauncher indicating a test tasks should always be executed (not matter of up-to-date or not)
* allow configuration from client side via `TestLauncher#alwaysRunTests()`

### Test Coverage

* can force execution of up-to-date test

## Story: Allow specification of tests from candidate invocations of a given test

A test class or method can run more than once. For example, the test class might be included by several different `Test` tasks,
or it might be a method on a superclass with several different subclasses, or it might have a test runner that defines several invocations for the test.
It would be nice to present users with this set of invocations and allow selection of one, some or all of them.

TBD

## Story: Allow specification of tests to run via package, patterns, TestDiscriptor inclusion/exclusion

### API proposal

* TestLauncher#withJvmTestPackages(String... packages)
* TestLauncher#excludeJvmTestPackages(String...)
* TestLauncher#excludeJvmTestMethods(String testClass, String... methods)
* TestLauncher#excludeJvmTestClasses(String...)

### Implementation

* add according inclusive pattern declared in TestLauncher to TestLauncher#testIncludePatterns / #testExcludePatterns

### Test Coverage

* can execute
	* all tests from specific package
 	* tests from a multiple packages
	* single test with regex include pattern
	* single test with an exclude pattern"
	* tests from specific package
	* tests from a single package using package exclude

## Story: Add ability to launch tests in debug mode

Need to allow a debug port to be specified, as hard-coded port 5005 can conflict with IDEA.
