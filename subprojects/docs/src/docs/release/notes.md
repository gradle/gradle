## New and noteworthy

Here are the new features introduced in this Gradle release.

### Installation via Gradle Wrapper is now multi process safe

In previous versions of Gradle it was possible for a Gradle distribution installed implicitly via the [Gradle Wrapper](userguide/gradle_wrapper.html) to be corrupted, 
or to fail to install, if more than one process was trying to do this at the same time. This was more likely to occur on a continuous build server than a developer workstation. 
This no longer occurs as the installation performed by the wrapper is now multi process safe.

**Important:** leveraging the new multi process safe wrapper requires updating the `gradle-wrapper.jar` that is checked in to your project. 
This requires an extra step to the usual wrapper upgrade process. 

First, update your wrapper as per usual by updating the `gradleVersion` property of the wrapper task in the build…

    task wrapper(type: Wrapper) {
        gradleVersion = "1.6"
    }

Then run `./gradlew wrapper` to update the wrapper definition. This will configure the wrapper to use and download Gradle 1.6 for future builds, 
but it has not updated the `gradle-wrapper.jar` that is checked in to your project. To do this, simply run `./gradlew wrapper` again. This is necessary as the wrapper 
jar is sourced from the Gradle environment that is running the build. 

If you are seeding a new project using an installation of Gradle 1.6 or higher, you do not need to run the wrapper task twice. It is only necessary when upgrading the 
wrapper from an older version.

### Force a task to run after another task, without adding a dependency (i)

In the past, the only way to ensure that Gradle ran one task after another was to add a dependency between those tasks. So if `Task A` must always run before `Task B`, you would
 say that `B.dependsOn(A)`. This has the added effect of forcing `Task A` to always run if `Task B` is executed.

In some cases, `dependsOn` is not the correct semantics. Let's say that you have a test-aggregation task that consumes the outputs of all of the test tasks. You want this aggregation
task to run _after_ all test tasks, but you do not necessarily want to force all test tasks to run. For this purpose, Gradle is introducing "task ordering" rules, the first of which
is `Task.mustRunAfter`. This rule does not change which tasks will be executed, but it does influence the order in which they will be executed.

    task runUnitTests(type: Test) { ... }
    task runIntegTests(type: Test) { ... }
    task createTestReports { ... }

    tasks.withType(Test) { testTask ->
        createTestReports.mustRunAfter(testTask)
    }

    task allTest(dependsOn: [runUnitTests, runIntegTests, createTestReports]) // This will run unit+integ tests and create the aggregated report
    task unitTest(dependsOn: [runUnitTests, createTestReports]) // This will run unit tests only and create the report
    task integTest(dependsOn: [runIntegTests, createTestReports]) // This will run integ tests only and create the report

Note that without 'mustRunAfter', `runUnitTests` task would run after the `createTestReport` task. But the using
`createTestReport.dependsOn(runUnitTests)` is not great, since that would make it hard to execute only `runIntegTests` and `createTestReport`
in the correct order. The `mustRunAfter` task ordering rule makes it easy to wire this logic into your build.

<!-- TODO Add link to docs for this feature, once they are in place. -->

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
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

### StartParameter.getMergedSystemProperties method is deprecated

This method is no longer used internally so it does not make sense to keep it in the public API.

## Potential breaking changes

### org.gradle.api.artifacts.ProjectDependency and org.gradle.api.plugins.ExtensionContainer now have an internal protocol

This means that the users should not create own implementations of org.gradle.api.artifacts.ProjectDependency or org.gradle.api.plugins.ExtensionContainer.
This change should not affect any builds because there are no known use cases supporting custom instances of these API classes.

### Renamed `add` method on PublicationContainer (incubating)

The [org.gradle.api.publish.PublicationContainer](javadoc/org/gradle/api/publish/PublicationContainer.html) introduced by the incubating publish plugins leverages the new support for
polymorphic DomainObject containers in Gradle. This change involved switching from the custom `add` methods to the standard `create`.
The semantics of the replacement methods is identical to those replaced.

This change does not effect publications added to the PublicationContainer using [a configuration block](javadoc/org/gradle/api/publish/PublishingExtension.html#publications),
but will impact publications added directly using `add()`.

### Task dependency graph changes
 // TODO:DAZ

### Changes to exceptions thrown on project evaluation
 // TODO:DAZ

### Incubating StartParameter.isParallelThreadCountConfigured method removed

It is not needed internally and it shouldn't be needed by the users, too.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
* Some Person - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
