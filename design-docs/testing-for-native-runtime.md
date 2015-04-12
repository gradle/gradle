## Story: Support CUnit test execution (done)

### Implementation

Generally, C++ test frameworks compile and link a test launcher executable, which is then run to execute the tests.

To implement this:
* Define a test source set and associated tasks to compile, link and run the test executable.
* It should be possible to run the tests for all architectures supported by the current machine.
* Generate the test launcher source and compile it into the executable.

## Story: Update binary types to permit multiple test frameworks

This story makes sure that subsequent test framework are discriminated.

### Implementation

- Add class org.gradle.nativebinaries.test.cunit.CUnitTestSuiteExecutableBinary which extends from TestSuiteExecutableBinary

### User visible change

Backward compatibility is kept and the following configure all test suite binary regardless of there framework.

    binaries.withType(TestSuiteExecutableBinary) {
      // Target all test binaries
    }

It will now be possible to configure the CUnit test suite binary individually like this:

    binaries.withType(CUnitTestSuiteExecutableBinary) {
      // Target only CUnit binaries
    }

### Test cases

    apply 'cunit'
    model { testSuites { mainTest { binaires.all { assert it instanceof CUnitTestSuiteExecutableBinary } } } }


## Story: RunTestExecutable can take arguments

This story make it possible to passed arguments to the test binary. Useful for more advance test framework like Google Test.
The mechanism to pass arguments will be modelled on the `Exec` task.

### User visible change

    tasks.withType(RunTestExecutable).all {
      args '--args-to-pass', '-v'
    }


## Story: Google Test support

This story adds support for the Google Test framework.

### Implementation

Note that the implementation is highly based on the current CUnit implementation.

 - Add class org.gradle.nativebinaries.test.googletest.GoogleTestTestSuite
 - Add class org.gradle.nativebinaires.test.googletest.GoogleTestTestSuiteExecutableBinary
 - Add class org.gradle.nativebinaires.test.googletest.plugins.GoogleTestPlugin
 - Add class org.gradle.nativebinaires.test.googletest.internal.DefaultGoogleTestTestSuite
 - Add class org.gradle.nativebinaires.test.googletest.internal.ConfigureGoogleTestTestSources
 - Add class org.gradle.nativebinaires.test.googletest.internal.CreateGoogleTestBinaries

### Open issues
 - Should the package name be 'googletest' or 'gtest' ('gtest' is used for there include namespace in c++)?
 - Should the C++ source set for Google Test named 'googletest' or 'gtest'?
 - How can we refactor CUnit code for reusability with Google Test? The process is the same - configure test source, create binaries, etc. - but yet different.


## Issue: Native unit test plug-ins do not allow you to apply other language plug-ins

e.g., this doesn't work:

    apply plugin: 'c'
    apply plugin: 'cpp'
    apply plugin: 'cunit'

    model {
       components {
          lib1(NativeLibrarySpec)
       }
    }

We explode in the ComponentModelBasePlugin because not all of the native tools are configured for the test binary.

# Open issues

* Need a `unitTest` lifecycle task, plus a test execution task for each variant of the unit tests.
* Need to exclude the `main` method from unit test sources.
* Generate a test launcher that is integrated with Gradle's test eventing.
* Automatically detect and register tests in test source files; don't require them to be explicitly registered. (Similar to JUnit and TestNG tests).
* Generate nice HTML reports for CUnit test output
