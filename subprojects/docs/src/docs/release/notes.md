Gradle 2.8 delivers performance improvements and a collection of general fixes and enhancements.

Large builds with many source files should see major improvements in incremental build speed.
This release also brings faster build script compilation, faster source compilation when using continuous build,
and general performance improvements that apply to all builds.

Building upon the recent releases, this release brings more improvements to the [Gradle TestKit](userguide/test_kit.html).
It is now easier to inject plugins under test into test builds.

Work continues on the new [managed model](userguide/new_model.html).
This release brings richer modelling capabilities along with interoperability improvements when dynamically depending on rule based tasks.

A Gradle release would not be complete without contributions from the wonderful Gradle community.
This release provides support for file name encoding in Zip files, support for more PMD features and other fixes from community pull requests.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Faster incremental builds

One of Gradle's key features is the ability to perform “incremental builds”.
This allows Gradle to avoid doing redundant work, by detecting that it can safely reuse files created by a previous build.
For example, the “class files” created by the Java compiler can be reused if there were no changes to source code or compiler settings since the previous build.
This is a [generic capability available to all kinds of work performed by Gradle](userguide/more_about_tasks.html#sec:up_to_date_checks).

This feature relies on tracking checksums of files in order to detect changes.
In this Gradle release, improvements have been made to the management of file checksums,
resulting in significantly improved build times when the build is mostly up to date (i.e. many previously created files were able to be reused).

Highly incremental builds of projects with greater than 140,000 files have been measured at 35-50% faster with Gradle 2.8.
Very large projects (greater than 400,000 files) are also significantly faster again, if there is ample memory available to the build process
(see [“The Build Environment”](userguide/build_environment.html) in the Gradle User Guide for how to control memory allocation).
Smaller projects also benefit from these changes.

No build script or configuration changes, beyond upgrading to Gradle 2.8, are required to leverage these performance improvements.

### Faster build script compilation

Build script compilation times have been reduced by up to 30% in this version of Gradle.

This improvement is noticeable when building a project for the first time with a certain version of Gradle, or after making changes to build scripts.
This is due to Gradle caching the compiled form of the build scripts.

The reduction in compilation time per script is dependent on the size and complexity of script.
Additionally, the reduction for the entire build is dependent on the number of build scripts that need to be compiled.

<!--
### Example new and noteworthy
-->

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

### Upgraded to Groovy 2.4.4

The Gradle API now uses Groovy 2.4.4. Previously, it was using Groovy 2.3.10. This change should be transparent to the majority of users; however, it can imply some minor breaking changes.
Please refer to the [Groovy language changelogs](http://groovy-lang.org/changelogs.html) for further details.

### New PMD violations due to type resolution changes

PMD can perform additional analysis for some rules (see above), therefore new violations may be found in existing projects.  Previously, these rules were unable to detect problems
because classes outside of your project were not available during analysis.

### Updated to CodeNarc 0.24.1

The default version of CodeNarc has been updated from 0.23 to 0.24.1. Should you want to stay on older version, it is possible to downgrade it using the `codenarc` configuration:

    dependencies {
       codenarc 'org.codenarc:CodeNarc:0.17'
    }

### Improved IDE project naming deduplication

To ensure unique project names in the IDE, Gradle applies a deduplication logic when generating IDE metadata for Eclipse and Idea projects.
This deduplication logic has been improved. All projects with non unique names are now deduplicated. here's an example for clarification:

Given a Gradle multiproject build with the following project structure

    root
    |-foo
    |  \- app
    |
    \-bar
       \- app

results in the following IDE project name mapping:

    root
    |-foo
    |  \- foo-app
    |
    \-bar
       \- bar-app

### Changes to the incubating integration between the managed model and the Java plugins

The Java plugins make some details about the project source sets visible in the managed model, to allow integration between rules based plugins and
the stable Java plugins. This integration has changed in this Gradle release, to move more of the integration into the managed model:

- The `sources` container is no longer added as a project extension. It is now visible only to rules, as part of the managed model.
- `ClassDirectoryBinarySpec` instances can no longer be added to the `binaries` container. Instances are still added to this container by the Java plugins for each source set,
however, additional instances cannot be added. This capability will be added again in a later release, to allow rules based plugins to define arbitrary class directory binaries.
- The Java plugins do not add instances to the `binaries` container until they are required by rules. Previously, the plugins would add these instances eagerly when the
source set was defined.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
