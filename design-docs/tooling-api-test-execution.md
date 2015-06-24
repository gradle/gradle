## Feature: Test Execution

## Story: Add ability to launch tests

### API proposal

* new method `newTestLauncher()` to ProjectConnection create test specific long running operation. returns `TestLauncher`
* `TestLauncher` can be configured to execute specific tests via `TestLauncher#withTests(TestOperationDescriptor)`
* `TestLauncher` interface contains all information about which tests should be executed.
* can configure `TestLauncher` via
	* TestLauncher#withJvmTestClasses(String...)
	* TestLauncher#withJvmTestMethods(String testClass, String... methods)

From a client this API can be used like:

		ProjectConnection connection = GradleConnector.newConnector()
		   	.forProjectDirectory(new File("someFolder"))
		   	.connect();

		try {
		   //run tests
		   connection.newTestLauncher()
			 .withTests(TestOperationDescriptor... testDescriptors)
			 .withJvmTestClasses('example.MyTest')
			 .withJvmTestMethods('example.MyTest2', "testMethod1", "testMethod2")
			 .addProgressListener(new MyTestListener(), EnumSet.of(OperationType.TEST))
		     .setStandardOutput(System.out)
		     .run();
		} finally {
		   connection.close();
	}

### Implementation


* Introduce new LongRunningOperation `TestLauncher`
* Add factory method `ProjectConnection#newTestRunner()`
* Introduce `TestLauncher`
* add `TestLauncher#withTest(TestOperationDescriptor)`
* change BuildModelActionRunner to run test tasks if TestConfiguration is provided
* calculate Test#testClassesDir / Test.classpath to find all tasks of type `org.gradle.api.tasks.testing.Test` containing matching pattern/tests
* execute matching Test tasks

### Test Coverage

* can execute
	* single JVM test class
	* multiple specific JVM test classes
	* single test method of JVM test class
	* multiple test methods of JVM test class
    * test methods specified using a test descriptor
* test will not execute if test task is up-to-date
* class included in multiple test tasks are executed multiple times (for each test task)
* tooling api operation fails with meaningful error message when no matching tests can be found.
* build should not fail if filter matches a single test task

### Open Issues

* With the current implementation all tasks of type `org.gradle.api.tasks.testing.Test` are executed with the pattern provided, even if those tasks have no matching tests declared.
* We need to improve how we deal with custom test runners, where there isn't a one-to-one mapping between test method and test execution. This is broken in the test progress reporting stuff.
* for staying forwards compatible we can't add arbitrary classes to `ProviderOperationParameters`. That might cause some friction in
the long run as we e.g. can't keep the test execution stuff maintained in a rich `ProviderOperationParameters#TestExecutionConfiguration` and have to use
`ProviderOperationParameters#testIncludes`+ `ProviderOperationParameters#testExcludes` instead.

## Story: Allow force execution of up-to-date test tasks

### Implementation

* add flag to TestLauncher indicating a test tasks should always be executed (not matter of up-to-date or not)
* allow configuration from client side via `TestLauncher#alwaysRunTests()`

### Test Coverage

* can force execution of up-to-date test

## Story: Allow force execution of up-to-date test tasks

### Implementation

* add flag to TestLauncher indicating a test tasks should always be executed (not matter of up-to-date or not)
* allow configuration from client side via TestLauncher#alwaysRunTests()

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
* TestLauncher#withTestsByPattern(String...)
* TestLauncher#excludeJvmTestPackages(String...)
* TestLauncher#excludeJvmTestMethods(String testClass, String... methods)
* TestLauncher#excludeTestsByPattern(String... patterns)
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
