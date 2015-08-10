## Feature: Test Execution

## Story: Rerun a previously run test

### API proposal

* new method `newTestLauncher()` to ProjectConnection create test specific long running operation. returns `TestLauncher`
* `TestLauncher` interface contains all information about which tests should be executed.

The API:

    interface ProjectConnection {
        TestLauncher newTestLauncher();
    }

    interface TestLauncher extends LongRunningOperation {
        TestLauncher withTests(TestOperationDescriptor... testDescriptors);

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
			 .withTests(descriptor1, descriptor2)
			 .addProgressListener(new MyTestListener(), EnumSet.of(OperationType.TEST))
		     .setStandardOutput(System.out)
		     .run();
		} finally {
		   connection.close();
	}

### Implementation

* ~~Given a `TestOperationDescriptor`, it is possible to calculate exactly which test task to run.~~
* ~~Introduce new `TestLauncher`~~
* ~~Add factory method `ProjectConnection#newTestRunner()`~~
* ~~Add a new protocol interface with a method that will accept a test execution request. The provider connection will implement this interface.~~
    * ~~For example see `InternalCancellableConnection`.~~
    * ~~Also update the docs on `ConnectionVersion4`.~~
* ~~Add a new `BuildAction` subtype to represent a test execution request.~~
* ~~Add a new `BuildActionRunner` subtype to handle this request.~~
* ~~Extract a decorator out of the current `BuildActionRunner` implementations to take care of wiring up listeners to send events back to build client.~~
	* ~~Ensure that listener failures are rethrown on the client side, as is done for the other kinds of operations. Refactor this on the client side so that the logic~~
	  ~~is in one place, rather than ad hoc per operation.~~
* ~~Change filter interfaces for `Test` to allow test class and method filters to be applied. Do not use patterns (except perhaps to initially get something working).~~
* ~~Run appropriate `Test` tasks based on the descriptors.~~
* ~~unchanged tests will reexecute without task marked as up-to-date.~~

### Test cases

* tests are executed:
    * ~~method A included in task A and task B. Descriptor for (method A, task A) is used, ensure task A only is executed~~
    * ~~method A included in task A and task B. Descriptor for (method A, task A) and (method A, task B) is used, ensure both tasks executed.~~
    * ~~using descriptor for (class A, task A) runs all methods for class A in task A.~~
* ~~build fails when the target test no longer exists.~~
* ~~does something reasonable when the target test task no longer exists, but the test still exists.~~
* ~~does something reasonable when the target test is no longer part of the target test task.~~
* ~~expected test progress events are received in each case~~
* ~~reasonable error message when target Gradle version does not support test execution~~
* ~~does something reasonable when continuous build is used.~~
* ~~`StartParameter.taskNames` returns something reasonable.~~

### Open questions

* Behaviour with continuous build.
* Behaviour when task no longer exists or no longer contains the requested test, but the test still exists (eg it has moved)

## Story: Add ability to launch JVM tests by class

### API proposal

Add methods to `TestLauncher` to request specific JVM test classes be executed.

    interface TestLauncher extends LongRunningOperation {
	    TestLauncher withJvmTestClasses(String...);
    }

### Implementation

* Change filter interfaces for `Test` to allow test class filters to be applied. Do not use patterns.
* Change `BuildController` and according `GradleLauncher` to allow registering custom `BuildConfigurationActions`.
* Register a custom `BuildConfigurationAction` to configure taskgraph with tasks based on provided `TestLauncher` configuration.
    * update handling of TestDescriptors to use the new custom `BuildConfigurationAction` to configure the tasks to run instead of configuring
      `StartParameter.taskNames`
* Run all `Test` tasks with filters applied.

### Test Coverage

* ~~can execute~~
	* ~~single JVM test class~~
	* ~~multiple specific JVM test classes~~
* ~~handles more than one test task~~
	* ~~class included in multiple test tasks is executed multiple times, once for each test task~~
	* ~~class included in multiple projects is executed multiple times, once for each test task~~
	* ~~request class A and class B, where class A is included in task A, class B is included in task B~~
	* ~~request class A, where class A is included in task A and not included in task B~~
	* ~~when configure-on-demand is being used with a multi-project build~~
* ~~tooling api operation fails with meaningful error message when no matching tests can be found~~
	* ~~class does not exist~~
	* ~~class does not define any tests or is not a test class~~
* ~~build should not fail if filter matches a single test task~~
* ~~expected test progress events are received in each case~~
	* ~~when configure-on-demand is being used with a multi-project build~~
* ~~tooling api operation fails with meaningful error message when no matching tests can be found~~
	* ~~class does not exist~~
	* ~~class does not define any tests or is not a test class~~
* ~~build should not fail if filter matches a single test task~~
* ~~expected test progress events are received in each case~~

## Story: Add ability to launch JVM tests by method

    interface TestLauncher extends LongRunningOperation {
	    TestLauncher withJvmTestMethods(String testClass, String... methods);
	}

### Implementation

* change filter interfaces for `Test` to allow test class and test method filters to be applied. Do not use patterns.

### Test cases

* ~~can execute~~
	* ~~single test method of JVM test class~~
	* ~~multiple test methods of JVM test class~~
* ~~methods that do not match are not executed.~~
* ~~expected test progress events are received in each case~~
* ~~tooling api operation fails with meaningful error message when no matching tests can be found~~
	* ~~class does not exist~~
	* ~~class does not define any tests~~
	* ~~class does not define any matching test methods~~
* ~~failing tests let the test launcher run throw an exception with a meaningful error message.~

## Story: Run only those test tasks that match the test execution request

Running all `Test` tasks with a filter has a serious downside: all the dependencies and finalizers for these tasks are run, even when not required.
For example, when a functional test suite requires some service to be provisioned and a data store of some kind to be created, this work will be on
every invocation of the test launcher, say when running unit tests, even when not required.

Instead, detect which `Test` task instances to run based on their inputs.

### Implementation

* Ideally, we should cache the result of test detection during `Test` execution, so that it can be reused to determine which `Test` instances to run.
	* Recalculate this when inputs have changed.
	* Will need to run the tasks that build the input classpaths.
	* Apply test detection.
	* Determine which `Test` tasks to run
	* Run these tasks and their dependencies and finalizers.
	* Do not run `Test` tasks that do no match, nor their dependencies or finalizers.
* Calculate Test#testClassesDir / Test.classpath to find all tasks of type `org.gradle.api.tasks.testing.Test` containing matching pattern/tests
* Execute matching Test tasks only

## Story: Rerun a failed JUnit test that uses a custom test runner

For example, a Spock test with `@Unroll`, or a Gradle cross-version test. In general, there is not a one-to-one mapping between test
method and test execution. Fix the test descriptors to honour this contract.

## Story: Add ability to launch tests in debug mode

Need to allow a debug port to be specified, as hard-coded port 5005 can conflict with IDEA.

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

### Test Coverage

* can execute
	* all tests from specific package
 	* tests from a multiple packages
	* single test with regex include pattern
	* single test with an exclude pattern"
	* tests from specific package
	* tests from a single package using package exclude
