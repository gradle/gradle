## New and noteworthy

Here are the new features introduced in this Gradle release.

### TestKit improvements

This release provide significant improvements to for consumers of the TestKit.

#### Debugging of tests executed with TestKit API from an IDE

Identifying the root cause of a failing functional test can be tricky. Debugging test execution from an IDE can help to discover problems
by stepping through the code line by line. By default TestKit executes functional tests in a forked daemon process. Setting up remote debugging for a daemon process
is inconvenient and cumbersome.

This release makes it more convenient for the end user to debug tests from an IDE. By setting the system property `org.gradle.testkit.debug` to `true` in the IDE run configuration,
a user can execute the functional tests in the same JVM process as the spawning Gradle process.

Alternatively, debugging behavior can also be set programmatically through the `GradleRunner` API with the method
<a href="javadoc/org/gradle/testkit/runner/GradleRunner.html#withDebug(boolean)">withDebug(boolean)</a>.

#### Unexpected build failure provide access to the build result

With previous versions of Gradle, any unexpected failure during functional test executions resulted in throwing a
<a href="javadoc/org/gradle/testkit/runner/UnexpectedBuildSuccess.html">UnexpectedBuildSuccess</a> or a
<a href="javadoc/org/gradle/testkit/runner/UnexpectedBuildFailure.html">UnexpectedBuildFailure</a>.
These types provide basic diagnostics about the root cause of the failure in textual form assigned to the exception `message` field. Suffice to say that a String is not very
convenient for further inspections or assertions of the build outcome.

This release also provides the `BuildResult` with the method <a href="javadoc/org/gradle/testkit/runner/UnexpectedBuildException.html#getBuildResult()">UnexpectedBuildException.getBuildResult()</a>.
`UnexpectedBuildException` is the parent class of the exceptions `UnexpectedBuildSuccess` and `UnexpectedBuildFailure`. The following code example demonstrates the use of build result from
fo an unexpected build failure:

    class BuildLogicFunctionalTest extends Specification {
        @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()

        def "can inspect build result for unexpected failure"() {
            given:
            buildFile << """
                task helloWorld {
                    doLast {
                        println 'Hello world!'
                    }
                }
            """

            when:
            def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('helloWorld')
                .buildAndFail()

            then:
            UnexpectedBuildSuccess t = thrown(UnexpectedBuildSuccess)
            BuildResult result = t.buildResult
            result.standardOutput.contains(':helloWorld')
            result.standardOutput.contains('Hello world!')
            !result.standardError
            result.tasks.collect { it.path } == [':helloWorld']
            result.taskPaths(SUCCESS) == [':helloWorld']
            result.taskPaths(SKIPPED).empty
            result.taskPaths(UP_TO_DATE).empty
            result.taskPaths(FAILED).empty
        }
    }

#### Ability to provide a Gradle distribution for test execution

In previous versions of Gradle, the TestKit API did not support providing a Gradle distribution for executing functional tests. Instead it automatically
determined the distribution by deriving this information from the build script that loads the `GradleRunner` class.

With this release, users can provide a Gradle distribution when instantiating the `GradleRunner`. A Gradle distribution, represented as a
<a href="javadoc/org/gradle/testkit/runner/GradleDistribution.html">GradleDistribution</a>, can be specified as Gradle version, a `URI` that hosts
the distribution ZIP file or a extracted Gradle distribution available on the filesystem. This feature is extremely useful when testing build logic
as part of a multi-version compatibility test. The following code snippet shows the use of a compatibility test written with
Spock:

    import org.gradle.testkit.runner.VersionBasedGradleDistribution

    class BuildLogicFunctionalTest extends Specification {
        @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()

        @Unroll
        def "can execute helloWorld task with Gradle version #gradleVersion"() {
            given:
            buildFile << """
                task helloWorld {
                    doLast {
                        println 'Hello world!'
                    }
                }
            """

            when:
            def result = GradleRunner.create(new VersionBasedGradleDistribution(gradleVersion))
                .withProjectDir(testProjectDir.root)
                .withArguments('helloWorld')
                .build()

            then:
            noExceptionThrown()
            result.standardOutput.contains(':helloWorld')
            result.standardOutput.contains('Hello world!')
            !result.standardError
            result.tasks.collect { it.path } == [':helloWorld']
            result.taskPaths(SUCCESS) == [':helloWorld']
            result.taskPaths(SKIPPED).empty
            result.taskPaths(UP_TO_DATE).empty
            result.taskPaths(FAILED).empty

            where:
            gradleVersion << ['2.6', '2.7']
        }
    }

## Story: Ability to provide Writers for capturing standard output and error

At the moment the standard output and error can only be resolved from the `BuildResult`. There's not direct output of these streams to the console. Users cannot provide their own OutputStreams
for other debugging or processing purposes. This story allows for specifying a Writer to the `GradleRunner` API that output will be forwarded to (e.g. `System.out`).

### Implementation

The `GradleRunner` abstract class will be extended to provide additional methods.

    public abstract class GradleRunner {
        public abstract GradleRunner withStandardOutput(Writer standardOutput);
        public abstract GradleRunner withStandardError(Writer standardError);
    }

* If no `Writer` is provided by the user, the Test Kit will not write the output to the console. Output from the test execution will be captured as part of the `BuildResult`.
* A user can provide `Writer` instances for standard output and/or standard error. The relevant output from the test execution will be forwarded to the provided Writers.
* If a user provides an `Writer`, then the corresponding standard output and/or error in the `BuildResult` provides the same information.

### Test Coverage

* If a user doesn't provide an `Writer`, then standard output and error are made available through the `BuildResult`.
* Providing a null `Writer` results in an exception thrown.
* A user can redirect the output to the console by providing `System.out` and `System.err` as input to a `Writer`. The standard output and error of the `BuildResult` provides the same information.
* A user can provide other instances of `Writer`. The standard output and error of the `BuildResult` provides the same information.
* `Writer` instances provided by the user capture output if an exception occurs during test execution.

### Open issues

* Using `System.out` and `System.err` as default? This might produce to much log output.

### Model rules improvements

TBD: DSL now supports `$.p` expressions in DSL rules:

    model {
        components {
            all {
                targetPlatform = $.platforms.java6
            }
        }
        components {
            def plat = $.platforms
            all {
                targetPlatform = plat.java6
            }
        }
    }

TBD: DSL now supports `$('p')` expressions in DSL rules:

    model {
        components {
            all {
                targetPlatform = $('platforms.java6')
            }
        }
    }

### Support for external dependencies in the 'jvm-components' plugin

It is now possible to reference external dependencies when building a `JvmLibrary` using the `jvm-component` plugin.

TODO: Expand this and provide a DSL example.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Changes to experimental integration between software model and Java plugins

TBD

- `binaries` container is now only visible to rules via model. The `binaries` project extension has been removed.

### Changes to experimental model rules DSL

TBD

- The `model { }` block can now contain only rule blocks.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
