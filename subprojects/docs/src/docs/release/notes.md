## New and noteworthy

Here are the new features introduced in this Gradle release.

### Easier debugging of TestKit functional tests

The [Gradle TestKit](userguide/test_kit.html) facilitates programmatic execution of Gradle builds for the purpose of testing plugins and build logic.
This release of Gradle makes it easier to use a debugger to debug build logic under test.

In order to provide an accurate simulation of a Gradle build, the TestKit executes the build in a _separate process_ by default.
This avoids interference to the build environment by the test environment and vice versa.
This does mean however, that executing a test via a debugger does not automatically allow debugging the build process.

To support debugging, it is now possible to specify that the build should be run in the same process as the test.
This can be done by setting the `org.gradle.testkit.debug` system property to `true` for the test process,
or by using the [`withDebug(boolean)`](javadoc/org/gradle/testkit/runner/GradleRunner.html#withDebug\(boolean\))
method of the `GradleRunner`.

Please see the [Gradle User Guide section on debugging with the TestKit](userguide/test_kit.html#test-kit-debug) for more information.

### Unexpected build failure provide access to the build result

With previous versions of Gradle TestKit, any unexpected failure during functional test executions resulted in throwing a
<a href="javadoc/org/gradle/testkit/runner/UnexpectedBuildSuccess.html">UnexpectedBuildSuccess</a> or a
<a href="javadoc/org/gradle/testkit/runner/UnexpectedBuildFailure.html">UnexpectedBuildFailure</a>.
These types provide basic diagnostics about the root cause of the failure in textual form assigned to the exception `message` field. Suffice to say that a String is not very
convenient for further inspections or assertions of the build outcome.

This release provides the `BuildResult` with the method <a href="javadoc/org/gradle/testkit/runner/UnexpectedBuildException.html#getBuildResult()">UnexpectedBuildException.getBuildResult()</a> for
diagnosing test execution failures. `UnexpectedBuildException` is the parent class of the exceptions `UnexpectedBuildSuccess` and `UnexpectedBuildFailure`.

### Ability to provide a Gradle distribution for test execution

In previous versions of Gradle, the TestKit API did not support providing a Gradle distribution for executing functional tests. Instead it automatically
determined the distribution by deriving this information from the build script that loads the `GradleRunner` class.

With this release, users can provide a Gradle distribution when instantiating the `GradleRunner`. A Gradle distribution, represented as a
<a href="javadoc/org/gradle/testkit/runner/GradleDistribution.html">GradleDistribution</a>, can be specified as Gradle version, a `URI` that hosts
the distribution ZIP file or a extracted Gradle distribution available on the filesystem. This feature is extremely useful when testing build logic
as part of a multi-version compatibility test. The following code snippet shows the use of a compatibility test written with
Spock:

    import org.gradle.testkit.runner.GradleDistribution

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
            def result = GradleRunner.create(GradleDistribution.withVersion(gradleVersion))
                .withProjectDir(testProjectDir.root)
                .withArguments('helloWorld')
                .build()

            then:
            noExceptionThrown()
            result.standardOutput.contains('Hello world!')
            result.taskPaths(SUCCESS) == [':helloWorld']

            where:
            gradleVersion << ['2.6', '2.7']
        }
    }

### Providing Writers for capturing standard output and error during test execution

Any messages emitted to standard output and error during test execution are captured in the `BuildResult`. There's no direct output of these streams to the console. This makes
diagnosing the root cause of a failed test much harder. Users would need to print out the standard output or error field of the `BuildResult` to identify the issue.

With this release, the `GradleRunner` API exposes methods for specifying `Writer` instances for debugging or purposes of further processing. A common use case is to forward
test execution output to the console. The following example demonstrates the use of the convenience method
<a href="javadoc/org/gradle/testkit/runner/GradleRunner.html#forwardOutput()">GradleRunner.forwardOutput()</a> to forward output to the console:

    class BuildLogicFunctionalTest extends Specification {
        @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()

        def "can forward standard output and error to console"() {
            given:
            buildFile << """
                task printOutput {
                    doLast {
                        println 'Hello world!'
                        System.err.println 'Expected error message'
                    }
                }
            """

            when:
            def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('printOutput')
                .forwardOutput()
                .build()

            then:
            noExceptionThrown()
            result.standardOutput.contains('Hello world!')
            result.standardError.contains('Expected error message')
        }
    }

### Model rules improvements

#### Declaring packages that belong to an API

It is now possible to declare the packages that make up the API of a JVM component. Declaring the API of a component is done using the `api { ... }` block:

    model {
        components {
            main(JvmLibrarySpec) {
                api {
                    // declares the package 'com.acme' as belonging to the public, exported API
                    exports 'com.acme'
                }
            }
        }
    }

Gradle will automatically create an API jar for the main component. Components that depend on that main component will compile against that API jar.
The API jar will only include classes that belong to those packages. As a consequence:
   - trying to compile a consumer that accesses a class which which doesn't belong to the list of exported packages will result in a compile time error.
   - updating a non-API class will not result in the compilation of downstream consumers.

#### `$.p` expressions in DSL rules

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

### Support for API dependencies in the 'jvm-components' plugin

The API of a JVM library consists of the API classes of the library, plus the API of dependent libraries that are defined as
"exported" in the dependency specification.

TODO: Expand this and provide a DSL example.

### Support for "discovered" inputs to incremental tasks

Incremental tasks can now register files as discovered inputs during task execution.

TODO: Expand this and provide an example.

### Tooling API improvements

#### Expose Eclipse builders and natures

Clients of the Tooling API now can query the list of Eclipse builders and natures via the
<a href="javadoc/org/gradle/tooling/model/eclipse/EclipseProject.html">EclipseProject</a> model. The result of the `EclipseProject.getProjectNatures()`
and `EclipseProject.getBuildCommands()` contain the builders and natures required for the target project as well as the customisation defined the
'eclipse' <a href="dsl/org.gradle.plugins.ide.eclipse.model.EclipseProject.html">Gradle plugin configuration</a>.

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

### Changes to native software model

TBD general warning about breaking changes, e.g. replacing `NativeExecutableBinarySpec#setExecutableFile` with `#getExecutable.setFile()`

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
