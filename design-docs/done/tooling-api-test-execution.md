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
