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

In some cases, `dependsOn` is not the correct semantics. A simple example is "clean" must always run before "build", but you don't always want to run "clean" whenever you run "build".
For this use case Gradle now has "task ordering" rules, the first of which is `Task.mustRunAfter`.
This rule does not change which tasks will be executed, but it does influence the order in which they will be executed.

    task clean { ... }
    task build { ... }

    build.mustRunAfter clean

In this example you can still execute `gradle clean` and `gradle build` independently, but running `gradle build clean` will cause 'clean' to be executed before 'build'.

Another example is a test-aggregation task that consumes the outputs of all of the test tasks.
You want this aggregation task to run _after_ all test tasks, but you do not necessarily want to force all test tasks to run.

    task runUnitTests(type: Test) { ... }
    task runIntegTests(type: Test) { ... }
    task createTestReports { ... }

    tasks.withType(Test) { testTask ->
        createTestReports.mustRunAfter(testTask)
    }

    task allTest(dependsOn: [runUnitTests, runIntegTests, createTestReports]) // This will run unit+integ tests and create the aggregated report
    task unitTest(dependsOn: [runUnitTests, createTestReports]) // This will run unit tests only and create the report
    task integTest(dependsOn: [runIntegTests, createTestReports]) // This will run integ tests only and create the report

Note that it would not be suitable to use `createTestReport.dependsOn(runUnitTests)` in this case,
since that would make it difficult to execute the integration tests and generate the report, _without_ running the unit tests.
The `mustRunAfter` task ordering rule makes it easy to wire this logic into your build.

See the User guide section on “[Ordering Tasks](userguide/more_about_tasks.html#sec:ordering_tasks)” for more information.

> We are incredibly grateful to Marcin Erdmann for taking on this long anticipated feature.
> The design and implementation of task ordering rules involved a deep understanding and refactoring of the Gradle Task execution engine, and Marcin took this on with gusto.
>
> Thanks, Marcin: I'm sure many Gradle users will appreciate your contribution.

### Jacoco Code Coverage Plugin
Gradle now ships with a Jacoco plugin to generate code coverage reports. JaCoCo is a free code coverage library for Java. 

To gather code coverage information for your java project, just apply the jacoco plugin:

    apply plugin:'jacoco'

and run the automatically created task "jacocoTestReport" which generates code coverage reports for the "test" task introduced by the `java` plugin.
After the build has finished you find the coverage report in different formats in `build/reports/jacoco/test`.

You configure every task of type `JacocoReport` to disable output formats and change the output directory. For example if you just want the `xml` coverage report that can be reused by your
favourite CI server, you can simply configure this:

    jacocoTestReport{
        reports {
            html.enabled false
            csv.enabled false
        } 
    }

In some scenarios it might be desirable to compute code coverage not by running tests, but by running the application itself.
Since the jacoco plugin can be used in combination with any `JavaExec` task of your build, it's quite simple to combine the `jacoco` plugin with the `run` task introduced by the `application` plugin:

    jacoco {
        applyTo run
    }
    
    task applicationCodeCoverageReport(type:JacocoReport){
        executionData run
        sourceSets sourceSets.main
    }

There was a great demand by the Gradle community for having a first class code coverage plugin in the Gradle distribution.
Therefore we're very happy that this plugin was provided by Andrew Oberstar, an energetic member of the Gradle community.

### Build Setup Plugin
This Gradle distribution introduces a `build-setup` plugin that supports users on initializing new Gradle projects.
We reworked the functionality for converting Maven projects into Gradle projects (formerly provided by the incubating `maven2Gradle` plugin) to be part of that new `build-setup` plugin.
Running `gradle setupBuild` in a directory with no `build.gradle` file a gradle project is initialized by

* If a `pom.xml` exists, the maven2Gradle task is triggered for generating a Gradle project out of the information gathered from the Maven POM files.
* If no `pom.xml` can be found an empty `build.gradle` file is generated.
* A `Wrapper` task is executed to generate all files needed run the build using the Gradle Wrapper.

As all new plugins, this Build Setup Plugin is marked as *incubating*. We have multiple plans to 
enhance the build initialization in future releases. Feel free to check out the current plans written up in the 
[Gradle design docs](https://github.com/gradle/gradle/blob/master/design-docs/build-initialisation.md) and give feedback!

### Support for JUnit @Category (i)
Thanks to a contribution by Uladzimir Mihura, Gradle now supports JUnit categories. Categories are a mechanism to label and group JUnit tests by using annotations. Having the following JUnit test code

    public interface FastTests { /* category marker interface */ }
    public interface SlowTests { /* category marker interface */ }

    public class MyTestClass {
        @Category(SlowTests.class)
        @Test public void testA() {
	    ...
	    ...
        }

        @Category(FastTests.class)
        @Test public void testB() {
	    ...
	    ...
        }
    }

you can simply configure your test task to run only specific categories:

    test { // run fast unit test only
        useJUnit {
            includeCategories 'org.gradle.categories.FastTests'
            excludeCategories 'org.gradle.categories.SlowTests'
        }
    }

<!-- TODO Add link to docs for this feature, once they are in place. -->

### Plugins can expose custom tooling models via the tooling API

TODO

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

### `StartParameter.getMergedSystemProperties()` method is deprecated

This method is no longer used internally so it does not make sense to keep it in the public API.

### `groovy` configuration is deprecated

Since Gradle 1.4, the preferred way to specify the Groovy library is to add it to the `compile` (or `testCompile`) configuration, rather than the `groovy` configuration.
Therefore, the `groovy` configuration is now deprecated. Simply replace `groovy` with `compile` in the `dependencies` block:

    dependencies {
        compile "org.codehaus.groovy:groovy-all:2.0.6"
    }

In some cases (for example if the Groovy Jar has been renamed), it may also be necessary to explicitly configure the `groovyClasspath` of `GroovyCompile` and `Groovydoc` tasks.

For additional background information about this change, see the [Groovy chapter](userguide/groovy_plugin.html#N1289B) of the Gradle user guide.

## Potential breaking changes

### `org.gradle.api.artifacts.ProjectDependency` and `org.gradle.api.plugins.ExtensionContainer` now have an internal protocol

This means that the users should not create own implementations of `org.gradle.api.artifacts.ProjectDependenc` or `org.gradle.api.plugins.ExtensionContainer`.
This change should not affect any builds because there are no known use cases supporting custom instances of these API classes.

### Renamed `add()` method on PublicationContainer (incubating)

The [org.gradle.api.publish.PublicationContainer](javadoc/org/gradle/api/publish/PublicationContainer.html) introduced by the incubating publish plugins leverages the new support for
polymorphic DomainObject containers in Gradle. This change involved switching from the custom `add` methods to the standard `create`.
The semantics of the replacement methods is identical to those replaced.

This change does not effect publications added to the PublicationContainer using [a configuration block](javadoc/org/gradle/api/publish/PublishingExtension.html#publications),
but will impact publications added directly using `add()`.

### Changes to exceptions thrown on project evaluation
 // TODO:DAZ

### Incubating `StartParameter.isParallelThreadCountConfigured()` method removed

It is not needed internally and it shouldn't be needed by the users, too.

### Upper bound removed from Tooling API `org.gradle.tooling.ModelBuilder`

With the introduction of support for custom tooling API models, the tooling API models are no longer required to extend the `org.gradle.tooling.model.Model` marker
interface. The upper bound `extends Model` has been removed from the type parameter of `ModelBuilder`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* Marcin Erdmann - added the ability to schedule one task to always run after another, without adding a hard dependency.
* Andrew Oberstar - added the jacoco code coverage plugin.
* Uladzimir Mihura - provide first-class support for JUnit @Category (GRADLE-2111).

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
