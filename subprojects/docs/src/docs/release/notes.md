## New and noteworthy

Here are the new features introduced in this Gradle release.

### Force a task to run after another task, without adding a dependency (i)

In the past, the only way to ensure that Gradle ran one task after another was to add a dependency between those tasks. So if `Task A` must always run before `Task B`, you would
 say that `B.dependsOn(A)`. This has the added effect of forcing `Task A` to always run if `Task B` is executed.

In some cases, `dependsOn` is not the correct semantics. A simple example is “clean” must always run before “build”, but you don't always want to run “clean” whenever you run “build”.
For this use case Gradle now has “task ordering” rules, the first of which is `Task.mustRunAfter`.
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

On behalf of the Gradle community, the Gradle development team would like to thank [Marcin Erdmann](https://github.com/erdi) for taking on this long anticipated feature.
The design and implementation of task ordering rules involved a deep understanding and refactoring of the Gradle Task execution engine, and Marcin took this on with gusto.

### JaCoCo Code Coverage Plugin (i)

Gradle now ships with a [JaCoCo](http://www.eclemma.org/jacoco/) plugin to generate code coverage reports. JaCoCo is a free code coverage library for Java.

To gather code coverage information for your java project, just apply the JaCoCo plugin:

    apply plugin: 'jacoco'

and run `gradle test jacocoTestReport` which generates code coverage reports for the “test” task introduced by the `java` plugin. The `JacocoReport` adds a `mustRunAfter` dependency on the
coverage data producing task ('test' in our example). After the build has finished you find the coverage report in different formats in `build/reports/jacoco/test`.

You can configure every task of type `JacocoReport` to enable other output formats than the default `HTML` report. For example, if you just want the `xml` coverage report that can be reused by your
favourite CI server, you can simply configure this:

    jacocoTestReport {
        reports {
            html.enabled = false
            csv.enabled = false
            xml.enabled = true
        } 
    }

In some scenarios it might be desirable to compute code coverage not by running tests, but by running the application itself.
Since the JaCoCo plugin can be used in combination with any `JavaExec` task of your build, it's quite simple to combine the `JaCoCo` plugin with the `run` task introduced by the `application` plugin:

    jacoco {
        applyTo run
    }
    
    task applicationCodeCoverageReport(type: JacocoReport) {
        executionData run
        sourceSets sourceSets.main
    }

This plugin was contributed by [Andrew Oberstar](https://github.com/ajoberstar), an energetic member of the Gradle community, and is a long requested out-of-the-box feature that is a very welcome addition.

### Build Setup Plugin (i)

Gradle 1.6 introduces a `build-setup` plugin that makes initializing new Gradle projects more convenient. 
It also supports bootstrapping the migration of an Apache Maven build to a Gradle build by generating a `build.gradle` file from a `pom.xml`.

The `build-setup` plugin is not a plugin that you manually apply to your project. You use it by executing the `setupBuild` task in a directory that does not contain a `build.gradle` file. 

Running `gradle setupBuild` in a directory with no `build.gradle` file will do the following:

* If a `pom.xml` exists, a `build.gradle` file is generated based on its content (e.g. equivalent dependency definitions).
* If no `pom.xml` exists, an empty `build.gradle` file is generated.
* The [Gradle Wrapper](userguide/gradle_wrapper.html) is installed for the project.

For more information please see the [User Guide chapter on this plugin](userguide/build_setup_plugin.html).

This plugin is an *incubating* feature and will improve and expand in scope in future releases. 
If you're interested in its progress and future, you can check out the [design spec](https://github.com/gradle/gradle/blob/master/design-docs/build-initialisation.md). 

### Support for JUnit `@Category` (i)

Thanks to a contribution by [Uladzimir Mihura](https://github.com/trnl), Gradle now supports [JUnit categories](https://github.com/junit-team/junit/wiki/Categories). 
Categories are a mechanism to label and group JUnit tests by using annotations. 

Given the following JUnit test code:

    public interface FastTests { /* category marker interface */ }
    public interface SlowTests { /* category marker interface */ }

    public class MyTestClass {
        @Category(SlowTests.class)
        @Test public void testA() {
	        …
        }

        @Category(FastTests.class)
        @Test public void testB() {
	        …
        }
    }

You can now easily configure your test task to run only specific categories:

    test { // run fast unit test only
        useJUnit {
            includeCategories 'org.gradle.categories.FastTests'
            excludeCategories 'org.gradle.categories.SlowTests'
        }
    }

The `includeCategories` and `excludeCategories` are methods of the [JUnitOptions](groovydoc/org/gradle/api/tasks/testing/junit/JUnitOptions.html) object and take 
the full class names of one or more category annotations to include or exclude.

### Incremental Tasks (i)

One of Gradle's most prized features is its ability to build a project incrementally. 
If tasks [declare their inputs and outputs](userguide/more_about_tasks.html#sec:task_inputs_outputs), Gradle can optimize the build by skipping the execution of each task 
whose inputs and outputs are unchanged since the previous execution (because the work would be completely redundant). This is known as “Incremental Build”. 
Gradle 1.6 introduces a related, [incubating](userguide/feature_lifecycle.html), feature that takes build optimization to the next level: “Incremental Tasks”.

An “incremental task” is able to selectively process inputs that have changed, avoiding processing inputs that have not changed and do not need reprocessing. 
Gradle provides information about changes to inputs to the task implementation for this purpose. 

To implement an incremental task, you add a `@TaskAction` method that takes a single parameter of type [IncrementalTaskInputs](dsl/org.gradle.api.tasks.incremental.IncrementalTaskInputs.html).
You can then supply an action to execute for every input file that is out of date, and another action to execute for every input file that has been removed.

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        inputs.outOfDate { change ->
            println "File ${change.file.name} is out of date"
        }

        inputs.removed { change ->
            println "File ${change.file.name} has been removed"
        }
    }

**Note:** Incremental task support is a complicated and challenging feature to support. Since the introduction of the implementation described above (early in the Gradle 1.6 release cycle),
discussions within the Gradle community have produced superior ideas for exposing the information about changes to task implementors. As such, the API for this feature will almost certainly
change in upcoming releases. However, please do experiment with the current implementation and share your experiences with the Gradle community. 
The feature incubation process (which is part of the Gradle [feature lifecyle](userguide/feature_lifecycle.html)) exists for this purpose of ensuring high quality 
final implementation through incorporation of early user feedback.

Be sure to check out the [User Guide chapter](userguide/incremental_tasks.html) and [DSL reference](dsl/org.gradle.api.tasks.incremental.IncrementalTaskInputs.html) for
more details on implementing incremental tasks.

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

### Apply plugins from init and settings scripts (i)

The `Gradle` type, which is configured by init scripts, and the `Settings` type, which is configured by settings scripts, now accept plugins.
This means that you can now package up init or settings logic in a binary plugin and apply this plugin from the appropriate script, in exactly
the same way you do for projects.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

### `groovy` configuration is deprecated

Since Gradle 1.4, the preferred way to specify the Groovy library is to add it to the `compile` (or `testCompile`) configuration, rather than the `groovy` configuration.
Therefore, the `groovy` configuration is now deprecated. Simply replace `groovy` with `compile` in the `dependencies` block:

    dependencies {
        compile "org.codehaus.groovy:groovy-all:2.0.6"
    }

In some cases (for example if the Groovy Jar has been renamed), it may also be necessary to explicitly configure the `groovyClasspath` of `GroovyCompile` and `Groovydoc` tasks.

For additional background information about this change, see the [Groovy chapter](userguide/groovy_plugin.html#N1289B) of the Gradle user guide.

### Renamed `add()` methods

TBD

### `StartParameter.getMergedSystemProperties()` method is deprecated

This method is no longer used internally so it does not make sense to keep it in the public API.

## Potential breaking changes

### `ProjectDependency` and `ExtensionContainer` now have an internal protocol

This means that the users should not create own implementations of `org.gradle.api.artifacts.ProjectDependenc` or `org.gradle.api.plugins.ExtensionContainer`.
This change should not affect any builds because there are no known use cases supporting custom instances of these API classes.

### Renamed `add()` method on PublicationContainer

The incubating [org.gradle.api.publish.PublicationContainer](javadoc/org/gradle/api/publish/PublicationContainer.html) introduced by the new publish plugins leverages the new support for
polymorphic domain object containers in Gradle. This change involved switching from the custom `add` methods to the standard `create`.
The semantics of the replacement methods is identical to those replaced.

This change does not effect publications added to the PublicationContainer using [a configuration block](javadoc/org/gradle/api/publish/PublishingExtension.html#publications),
but will impact publications added directly using `add()`.

### Changes to exceptions thrown on project evaluation

The exception thrown by Gradle when on build script error or other configuration problem has changed. All such exceptions are now chained in ProjectConfigurationException.
This change will only impact code that explicitly catches and processes an exception thrown by Gradle when configuring a project.

### Incubating `StartParameter.isParallelThreadCountConfigured()` method removed

It is not needed internally and it shouldn't be needed by the users, too.

### Upper bound removed from Tooling API `ModelBuilder`

In Gradle 1.6, we've started work to support custom tooling API models. As a result, the tooling API models are no longer required to extend the
`org.gradle.tooling.model.Model` marker interface. The upper bound `extends Model` has been removed from the type parameter of `ModelBuilder`.

### Tooling API `ProjectConnection.model()` no longer throws `UnknownModelException`

With support for custom tooling API models, it is no longer possible to determine whether a model is supported without
configuring the target build. This exception is now thrown when the result is requested, rather than when the builder is created.

### Wrapper environment variable `GRADLE_WRAPPER_ALWAYS_UNPACK` and `GRADLE_WRAPPER_ALWAYS_DOWNLOAD` no longer supported

The Gradle wrapper no longer supports the `GRADLE_WRAPPER_ALWAYS_UNPACK` and `GRADLE_WRAPPER_ALWAYS_DOWNLOAD` environment variables.
Instead, the wrapper is now much better at recovering from failures to download or unpack the distribution.

### More packages included in default imports

The set of default imports is now generated directly from the Gradle API. This means that the default imports now includes a number of additional packages
that were not previously imported by default. These packages may contain classes that conflict with other imports present in your build scripts.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Marcin Erdmann](https://github.com/erdi) - added the ability to schedule one task to always run after another, without adding a hard dependency.
* [Andrew Oberstar](https://github.com/ajoberstar) - added the JaCoCo code coverage plugin.
* [Uladzimir Mihura](https://github.com/trnl) - provide first-class support for JUnit @Category (GRADLE-2111).
* [Xavier Ducrohet](https://github.com/ducrohet) - fix Success Rate display in test report overview page (GRADLE-2729).

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
