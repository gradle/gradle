# Testing custom build logic

## High-level goals

* Writing and executing functional tests against build scripts via Tooling API.
* Providing utility methods for common operations in tests e.g. creating directories/files.
* Generating Ivy and Maven repositories for testing purposes. Creating modules and artifacts in a repository to emulate dependency management behavior.

The test-kit will be agnostic of the test framework preferred by the user (e.g. JUnit, TestNG, Spock). Adapters will be provided to make it easy to integrate the test-kit with a specific test framework.

## Technical details

* Except for the Groovy-based test adapter all code will be developed in Java.
* The test-kit and all test adapters are implemented in the same repository as Gradle core. The test-kit classes are shipped with Gradle core distribution and can be imported as needed.

# Milestone 1

The first milestone lays the foundation for defining and executing functional tests. The goal should be a solid implementation based on the tooling API and integration
with the plugin development plugin.

## Story: User creates and executes a functional test using the test-kit (DONE)

A set of interfaces/builders will be developed to provide programmatic execution of Gradle builds. Tests are executed with the Tooling API.

### User visible changes

To use the test-kit, the build author must declare a dependency on the test-kit:

    dependencies {
        testCompile gradleTestKit()
    }

Initially, this will be available only as a local dependency.

The test-kit does not have an opinion about the used test framework. It's up to the build logic to choose a test framework and declare its dependency.

    dependencies {
        // Declare the preferred test framework
        testCompile '...'
    }

As a user, you write your functional test by using the following interfaces:

    package org.gradle.testkit.functional;

    public abstract class GradleRunner {
        File getWorkingDir();
        void setWorkingDir(File workingDirectory);
        List<String> getArguments();
        void setArguments(List<String> string);
        void useTasks(List<String> taskNames);
        BuildResult succeeds() throws UnexpectedBuildFailure;
        BuildResult fails() throws UnexpectedBuildSuccess;

        public static GradleRunner create() { ... }
    }

    public interface BuildResult {
    }

A functional test using Spock could look as such:

    class UserFunctionalTest extends Specification {
        def "run build"() {
            given:
            File testProjectDir = new File("${System.getProperty('user.home')}/tmp/gradle-build")
            File buildFile = new File(testProjectDir, 'build.gradle')

            buildFile << """
                task helloWorld {
                    doLast {
                        println 'Hello world!'
                    }
                }
            """

            expect:
            def runner = GradleRunner.create().with {
                workingDir = testProjectDir
                arguments << "helloWorld"
            }
            runner.succeeds()
        }
    }

### Implementation

* A new module named `test-kit-functional` will be created in Gradle core.
* The implementation will be backed by the Tooling API. The implementation may use internal parts of the tooling API and is not restricted to the public APIs.
    * This will mean that daemons are left behind by the tests. This will be addressed in later stories.
* A test build should use the Gradle installation that is running the test.
    * When running from a `Test` task, use the Gradle installation that is running the build.
    * When importing into the IDE, use the Gradle installation that performed the import.
    * Can infer the location of the Gradle installation based on the code-source of the test kit classes (reuse `GradleDistributionLocator` in some form for this).
    * Possibly provide some override for our functional tests to use, to run from the classpath.
* No environmental control will be allowed (e.g. setting env vars or sys props).
* Add (or expand) a sample for building and testing a plugin and task implementation.
* Add some brief user guide material.

### Test coverage

* Execute a build that is expected to succeed.
    * No exception is thrown from `GradleRunner`.
* Execute a build that is expected to succeed but fails.
    * A `UnexpectedBuildFailure` is thrown, with some reasonable diagnostics.
    * Something useful is done with the build standard output and error.
* Execute a build that is expected to fail.
    * No exception is thrown from `GradleRunner`.
* Execute a build that is expected to fail but succeeds.
    * A `UnexpectedBuildSuccess` is thrown, with some reasonable diagnostics.
    * Something useful is done with the build standard output and error.
* A build can be provided with command line arguments. Upon execution the provided options come into effect.
* Reasonable diagnostics when badly formed Gradle arguments or working directory.
* Tooling API mechanical failures produce good diagnostic messages.
    * For example, bad java home or jvm args in `gradle.properties`.
    * When daemon dies, say by build script calling `Runtime.halt()`.
* IDEA and Eclipse projects are configured appropriately to use test-kit. Manually verify that this works as well (in IDEA say).

### Open issues

* Reuse contract? Can `TestRunner` be reused to run multiple builds? Is it reset between builds?
* Need to do something useful with the build standard output and error when `succeeds()` or `fails()` throw an exception.
* Thread-safety contract?
* Should we be doing any implicit normalization of failure diagnostic messages?

## Story: Functional test queries the build result (DONE)

Add methods to `BuildResult` to query the result.

    public interface BuildResult {
        String getStandardOutput();
        String getStandardError();
        List<String> getExecutedTasks();
        List<String> getSkippedTasks();
    }

### Implementation

- Executed tasks and skipped tasks should be calculated using progress events generated by the tooling API, rather than scraping the output.

### Test cases

* Change test cases above to verify:
    * Standard output retrieved from `GradleRunner` contains `println` or log message.
    * Standard error retrieved from `GradleRunner` contains error message.
* A build can be executed with more than one task.
    * Tasks that are marked SKIPPED, UP-TO-DATE or FAILED can be retrieved as skipped tasks from `GradleRunner`.
    * All successful, failed or skipped tasks can be retrieved as executed tasks from `GradleRunner`.
* A build that has  `buildSrc` project does not list executed tasks from that project when retrieved from `GradleRunner`.

## Story: Test daemons are isolated from the environment they are running in (DONE)

The previous stories set up the basic mechanics for the test-kit. Daemons started by test-kit should be isolated from the machine environment:

- Test-kit uses only daemons started by test-kit.
- Test kit daemons are not affected by build using custom gradle user home dir (i.e. isolate daemon base dir)
- Configuration in ~/.gradle is ignored, such as `init.gradle` and `gradle.properties`
- Daemons use default JVM arguments that are more appropriate to test daemons. Best option might be to use the JVM defaults.
- Daemons are reused by tests.
- Daemons are stopped at the end of the tests.
- Daemons use a short idle timeout, say several minutes.

### Implementation

- Allow “working space” for runner to be specified, defaulting to `«java.io.tmpdir»/gradle-test-kit-«user name»`
    - Use for default gradle user home dir
    - Use for daemon base dir
- Reuse the temporary directory within the test JVM process, so that daemons are reused for multiple tests.
- Kill off the daemons when the JVM exits (See `DefaultGradleConnector#close()`). If required, split out another internal method to stop the daemons.

### Test cases

* Test are executed in dedicated, isolate daemon instance.
    * If no daemon process exists, create a new one and use it. Regular Gradle build executions will not use the daemon process dedicated for test execution.
    * If a daemon process already exists, determine if it is a daemon process dedicated for test execution. Reuse it if possible. Otherwise, create a new one.
    * A daemon process dedicated for test execution only use its dedicated JVM parameters. Any configuration found under `~/.gradle` is not taken into account.
    * Daemons are stopped at the end of the test.
* Two gradle runners sharing the same working space can be run at the same time
    * No daemons are left behind.
* Runner fails early if working space dir cannot be created or written to
* Executing a build with a `-g` option does not affect daemon mechanics (i.e. daemon base dir is not under `-g`)

### Open issues

* The "working space" is defined as temporary directory that is deleted eventually by the JVM. At the moment the user cannot set a custom "working directory" e.g.
for debugging purposes. Should we potentially allow the user to set a Gradle user home directory via the `GradleRunner` API?
* Looking at `DefaultGradleConnector#close()` it seems like it was introduced with Gradle 2.2. Later stories will allow executing a build with an older version.

We'll support that scenario when we get to the corresponding story.

## Story: Functional test defines classes under test to make visible to test builds (DONE)

Provide an API for functional tests to define a classpath containing classes under test:

    public interface GradleRunner {
        List<URI> getClassesUnderTest();
        void setClassUnderTest(Collection<URI> classpath);
    }

This classpath is then available to use to locate plugins in a test build, as if they were published to the plugin portal:

    plugins {
        id 'com.my-org.my-plugin'
    }

    task someTask(type: MyTaskType) { ... }

    model {
        tasks {
            otherTask(MyTaskType) { ... }
        }
    }

### Implementation

- Add an internal Tooling API mechanism to attach this to a build request:
    - Provide the plugin classpath when configuring the `BuildLauncher`. Perhaps add an internal subtype with the appropriate methods.
    - Pass the plugin classpath from Tooling API consumer to provider. Add to `ConsumerOperationParameters` and `ProviderOperationParameters`.
    - Send the classpath between provider and daemon. Add to `BuildActionParameters` (or perhaps `BuildModelAction`).
- Based on this, add another internal plugin resolver that uses the supplied classpath to locate plugins:
    - Perhaps have `InProcessBuildActionExecuter` talk to some service to pass this classpath through to the plugin resolution mechanics, or pass this
      through when creating the `GradleLauncher`.
    - Define a new `PluginResolver` implementation that loads the plugins in a ClassLoader scope whose parent is the Gradle API scope.

This diagram shows the intended ClassLoader hierarchy. The piece to be added is shaded blue:

<img src="img/plugins-under-test-classloaders.png">

### Test coverage

- Test build can apply a plugin by id.
- Plugin code can use Gradle API classes.
- Build script can use plugin classes, when applied. Cannot use plugin classes when not applied.
- Diagnostic message for 'plugin not found' failure includes some details of the classpath it searched.

### Open issues

- How can a plugin under test apply another plugin under test programmatically, say in its `apply()` method?

# Milestone 2

## Story: IDE user debugs test build (DONE)

By default functional tests are executed in a forked daemon process. Debugging test execution in a different JVM other than the "main" Gradle process would require additional setup from the end user.
This story improves the end user experience by allowing for conveniently step through code for debugging purposes from the IDE without the need for any complicated configuration.

### User visible changes

The `GradleRunner` abstract class will be extended to provide additional methods.

    public abstract class GradleRunner {
        public abstract boolean isDebug();
        public abstract GradleRunner withDebug(boolean debug);
    }

### Implementation

* When debug is enabled, run the build in embedded mode by setting `DefaultGradleConnector.embedded(true)`.
* Can enable debug via `GradleRunner.withDebug(boolean)`.
* Debug is automatically enabled when `Test.debug` is true.
* Debug is automatically enabled when test is being run or debugged from an IDE. In the IDE test execution run configuration, the system property `org.gradle.testkit.debug` has to be set to `true`. A
later story dealing with the plugin development plugin can deal with the automatic setup of the system property by pre-configuring the `idea` and `eclipse` plugin.

### Test coverage

* The debug flag is properly passed to the tooling API.
* All previous features work in debug mode. Potentially add a test runner to run each test in debug and non-debug mode.
* Manually verify that when using an IDE, a breakpoint can be added in Gradle code (say in the Java plugin), the test run, and the breakpoint hit.
* If the debug flag is not set explicitly through the API or run from the IDE, functional tests run in a forked daemon process.

### Open issues

- Port number? (Is this even an issue? Probably not because the tests are executed in the same JVM.)
- Do we expect classloading issues between user-defined dependencies and Gradle core dependencies when running in embedded mode?
- How do we reliably determine that the build is executed from an IDE?
- Should the integration with `Test.debug` be moved to the story that addresses the plugin development plugin?

## Story: Developer inspects build result of unexpected build failure (DONE)

This story adds the ability to understand what happened with the test when it fails unexpectedly.

- UnexpectedBuildFailure and Success should have-a BuildResult
- Tooling API exceptions and infrastructure failures should be wrapped and provide build information (e.g. stdout)

## Story: Test build code against different Gradle versions (DONE)

Extend the capabilities of `GradleRunner` to allow for testing a build against more than one Gradle version. The typical use case is to check the runtime compatibility of build logic against a
specific Gradle version. Example: Plugin X is built with 2.3, but check if it is also compatible with 2.2, 2.4 and 2.5.

### User visible changes

A user interacts with the following interfaces/classes:

    package org.gradle.testkit.runner;

    public interface GradleDistribution<T> {
        T getHandle();
    }

    public final class VersionBasedGradleDistribution implements GradleDistribution<String> { /* ... */ }
    public final class URILocatedGradleDistribution implements GradleDistribution<URI> { /* ... */ }
    public final class InstalledGradleDistribution implements GradleDistribution<File> { /* ... */ }

    package org.gradle.testkit.runner;

    public abstract class GradleRunner {
        public static GradleRunner create(GradleDistribution<?> gradleDistribution) { /* ... */ }
    }

A functional test using Spock could look as such:

    class BuildLogicFunctionalTest extends Specification {
        @Unroll
        def "run build with Gradle version #gradleVersion"() {
            given:
            @Rule final TemporaryFolder testProjectDir = new TemporaryFolder()
            File buildFile = testProjectDir.newFile('build.gradle')

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
            }

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

### Implementation

* The tooling API uses the provided Gradle distribution. Any of the following locations is valid:
    * Gradle version String e.g. `"2.4"`
    * Gradle URI e.g. `new URI("http://services.gradle.org/distributions/gradle-2.4-bin.zip")`
    * Gradle installation e.g. `new File("/Users/foo/bar/gradle-installation/gradle-2.4-bin")`

### Test coverage

* `GradleRunner` throws an exception if `GradleDistribution` is provided that doesn't match the supported types.
* A test can be executed with Gradle distribution provided by the user. The version of the distribution can be a different from the Gradle version used to build the project.
* A test can be executed with a series of Gradle distributions of the same type or different types.
* Tests can be executed in parallel for multiple Gradle versions.
* A test using a unknown or unsupported for a Gradle distribution fails the build.

### Open issues

* Should we provide a JUnit Runner implementation to simplify definition of Gradle distributions? The use case would be a matrix combination of multiple Gradle distributions and a list of `where`
arguments.
* Do we need to deal with TestKit runtime behavior backward compatibility e.g. no isolated daemon environment when executing test with a Gradle version that doesn't support it yet?

## Story: Ability to provide Writers for capturing standard output and error (DONE)

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

## Story: Test kit does not require any of the Gradle runtime (DONE)

This story improves usability of the test kit by not imposing any dependencies beyond the `gradle-test-kit` and `gradle-tooling-api` jars (including no transitive dependencies).

### Implementation

- Push responsibility for finding a Gradle distribution based on a class (i.e. what GradleDistributionLocator) does into the tooling API
- Remove the dependency on `gradle-core` in the test kit project

### Test Coverage

- User tests cannot access classes from `gradle-core` (or any other part of the Gradle runtime) in tests where `gradleTestKit()` was used
- Configuration containing just `gradleTestKit()` contains no other files than `gradle-test-kit` and `gradle-tooling-api`

## Story: GradleRunner functionality is verified to work with all "supported" Gradle versions (DONE)

The TestKit allows for executing functional tests with a Gradle distribution specified by the user. `GradleRunner` passes the provided
distribution to the Tooling API to execute Gradle. For the most part the internal implementation of the Tooling API build execution
uses a conservative set of features though there's no assurance that a Tooling API will work with older versions of Gradle
in this context. This story aims for implementing appropriate test coverage to ensure backward compatibility or graceful handling of
unsupported functionality for other versions of the Tooling API.

### Implementation

* The goal is to discover which version of the Gradle runtime can be used to execute a test. For each test verify the range from version 1.0 up
to the latest version.
* Based on the findings, introduce annotation(s) that indicate if a specific feature is supported for a test or not. The following scenarios are known
potential issues for some versions of Gradle:
    * Does it use the `GradleRunner.withPluginClasspath()` method? (introduced in 2.8)
    * Does it require inspecting the build text output? (doesn’t work in debug mode prior to Gradle 2.9)
    * Does it not work / not make sense in debug mode? (i.e. what we currently use `@NoDebug` to indicate).
* The annotation(s) controlling the scenario automatically determine the Gradle version(s) used for executing the test. The Gradle version used for testing
is injected via `GradleRunner.withGradleVersion(String)`. This logic should be implemented in the JUnit rule `GradleRunnerIntegTestRunner`.
* The annotation(s) controlling the scenario need to be able to indicate if a scenario is supported or not e.g. `@PluginClasspathInjection(supported = false)`.
* A TestKit feature that is not supported by the Gradle version used to execute the test should behave in a reasonable manner e.g. provide
a human-readable error message that explains why this feature cannot be used.

### Test Coverage

* All TestKit integration tests in Gradle core are exercised.
* Test passes for feature with Gradle versions supporting it.
* Test is skipped for Gradle versions that do not support feature based on assigned annotation.
* Assigned Gradle versions used for testing are properly evaluated and used for execution.

### Open issues

* Account for increased build time on CI. We could either re-shard the jobs or create dedicated jobs that executed the compatibility tests with different versions of Java.
* To determine the target Gradle version under test based on the provided distribution, the Tooling API model is queries which probably has a small overhead in terms of
execution performance. Are we OK with this overhead or is there a better way to determine the target Gradle version?

## Story: Audit existing tests and improve the test coverage for TestKit (DONE)

In a previous story, support for cross-version compatibility tests have been put in place. The goal of this story to audit the existing tests and improve the test coverage.

### Implementation

* _Reducing the number of tests:_ We should audit the tests and try and remove ones that aren’t adding enough value. There are a lot of tests that fairly closely overlap.
We can get the number (and build time) down by collapsing some of these tests.
* _Improving the coverage by removing arbitrary restrictions on tests:_ A lot of tests are reading from the output, or inspecting the task list unnecessarily.
This limits them to running with recent versions of Gradle. We should confine such tests to explicit tests for those features, so more of the tests can run on older versions.
* _Increase test coverage of a specific Gradle versions_ used to test a scenario by removing some of the constrains explained in the previous two points.
This work would change how we apply the range of test executions for a test class (e.g. Gradle 2.5, latest release and version under development). Instead of creation test
executions on the class level, this needs to be done on the level of a test method. The approach would require a different implementation than `AbstractMultiTestRunner`.

### Test coverage

* The number of tests is reduced.
* Optimally test execution time is shorter.
* Specific test methods can be tested against earlier versions of Gradle.

# Milestone 3

This milestone focuses on making TestKit more convenient to use for plugin developers by reducing boiler plate logic. Another aspect addresses the packaging of TestKit
and the Gradle API to avoid classpath issues.

## Story: Plugin development plugin automatically injects plugin classpath (DONE)

Plugin developers using TestKit need to inject the classes-under-test by using the method `withPluginClasspath(Iterable<? extends File> classpath)`. TestKit proposes ways
to determine the classpath in the user guide. This approach requires boiler plate code that needs to be copy/pasted from project to project. The goal of this story is
to provide a simple way to remove boiler plate code by enhancing the [plugin development plugin](https://docs.gradle.org/current/userguide/javaGradle_plugin.html). The creation
and usage of the `classpath.properties` method demonstrated in the user guide will be abstracted from the user and turned into an automated process.

### Implementation

* The plugin automatically creates a new task for generating the manifest file named `pluginClasspathManifest`.
    * Create implementation as custom task.
    * The classpath generated by the task is stored in a plain text file. Each classpath entry is written line-by-line. The file separator used for the entries is "/" for all
    operating systems.
    * The task defines inputs and outputs for the task for supporting incremental build functionality.
    * By default the task uses the `runtimeClasspath` of the `sourceSets.main` to derive the classpath. This input is reconfigurable.
    * The output file for the generated classpath is `$buildDir/$task.name/plugin-under-test-metadata.properties`. The task automatically creates the output directory if it doesn't exist yet.
    The file name is not configurable though the output directory is.
    * The contents of the properties file contains a single property `implementation-classpath`. The assigned value is the runtime classpath.
* By default the dependency on `gradleTestKit()` is automatically assigned to compile configuration of the `sourceSets.test`. A user can declare one or many source sets to be used
for functional testing with TestKit.
* An extension is exposed that allows for configuring functional testing.
    * The source set for the project containing the code under test. Default value: `sourceSets.main`.
    * The test source sets that require the code under test to be visible to test builds. Default value: `sourceSets.test`.
* Automatically assign the task `pluginClasspathManifest` to the test source sets runtime configuration via `dependencies.<runtime-configuration> files(pluginClasspathManifest)`.
* Introduce a new method `GradleRunner.withPluginClasspath()`. If called the `plugin-under-test-metadata.properties` is read, the classpath constructed and
provided to the call `AbstractLongRunningOperation.withInjectedClassPath(ClassPath classpath)`.
    * The method call is only made if the constructed classpath is not empty and the target Gradle version supports the API (>= 2.8).
    * If the user provided a custom classpath then classpath provided by `plugin-under-test-metadata.properties` is overridden.
* The plugin will not provide direct support for implementing plugins in languages other than Java. If a user prefers to write a plugin in a different JVM language, the build script
 needs to apply the corresponding JVM language plugin e.g. `apply plugin: 'groovy'` for a plugin written in Groovy. There's nothing to be done for the plugin-dev-plugin.
* Add or expand sample to demonstrate this feature.
* Add some brief user guide material in TestKit guide. The documentation for the plugin-dev-plugin should link to it.

### API

The extension is defined with the following properties:

    public class GradlePluginDevelopmentExtension {
        private SourceSet pluginSourceSet;
        private Set<SourceSet> testSourceSets;

        // getters/setters
        ...

        public void testSourceSets(SourceSet... testSourceSets) {
            ...
        }
    }

    project.getExtensions().create("gradlePlugin", GradlePluginDevelopmentExtension.class);

### User visible changes

The usage of the extension looks as follows:

    gradlePlugin {
        pluginSourceSet sourceSets.main
        testSourceSets sourceSets.test, sourceSets.functionalTest
    }

### Test Coverage

* By applying this plugin sensible default values are set for plugin classpath generation and its extension.
* A user can manually execute the task for generating classpath by executing the `pluginClasspathManifest` task.
* The `pluginClasspathManifest` task is executed before the `Test` task is executed that corresponds to the source set declaring the dependency on the TestKit API.
* Default values for inputs/outputs of the task `pluginClasspathManifest` are used.
* The generated classpath file contains the expected entries.
* A user can only configure the input of the `pluginClasspathManifest` task but not its output.
* The generated classpath file is read when invoking `GradleRunner.create()`.
    * An exception is thrown, if the file does not exist.
    * An exception is thrown, if the file cannot be parsed or the classpath cannot be constructed from its contents.
* The end user is provided with automatic plugin classpath injection with just the default conventions.
    * Automatic injection of the classpath only works if the target Gradle version is >= 2.8.
    * The plugin classpath can be provided for multiple test source sets.
    * If the user calls the method `GradleRunner.withPluginClasspath(Iterable<? extends File> classpath)` for the same `GradleRunner` instance, the classpath set by the last method
invocation wins.
* Manually verify that executing tests in the IDE (say IDEA) works reasonably well. Document any unforeseen caveats.

## Story: Isolate external dependencies used by Gradle runtime from user classpath (DONE)

### Estimate

6-20 days

See [features/plugin-development-gradle-dependency-isolation/README.md](plugin-development-gradle-dependency-isolation).

# Milestone 4

## Story: Integration with Jacoco plugin

If the user applies the Jacoco plugin, the plugin development plugin should properly configure it to allow for generation of code coverage metrics. This functionality has been
requested on the [Gradle forum](https://discuss.gradle.org/t/gradle-plugins-integration-tests-code-coverage-with-jacoco-plugin/12403).

### User visible changes

* Code coverage metrics can be generated by configuring the Jacoco plugin.
    * Java agent is hooked up to daemon JVM executing the tests via `GradleRunner.create().withJvmArguments(...)`.
    * The version of Jacoco used for generating the metrics is one provided by the property `toolVersion`.
    * Configure plugin to add a `JacocoReport` task for functional tests.
* Add or expand sample to demonstrate this feature.
* Add some brief user guide material.

### Test coverage

* If the consuming build script does not apply the Jacoco plugin, no code coverage metrics are generated.
* If the consuming build script applies the Jacoco plugin, code coverage metrics are generated when executing functional tests.
    * Binary and HTML-based reports can be generated.
    * Reports are generated in a dedicated report directory.
    * Code coverage can be generated when tests are executed with and without debug mode. The reports should reflect the same result.

## Story: User can create repositories and populate dependencies

A set of interfaces will be developed to provide programmatic creation of a repositories and published dependencies. A benefit of this approach is that a test setup doesn't need to reach out to the
internet for interacting with the dependency management mechanics. Furthermore, the user can create artifacts and metadata for test scenarios modeling the specific use case.

### User visible changes

As a user, you interact with the following interfaces to create repositories and dependencies:

    package org.gradle.testkit.fixtures;

    public interface Repository {
        URI getUri();
        Module module(String group, String module);
        Module module(String group, String module, Object version);
    }

    public interface Module<T extends Module> {
        T publish();
    }

    package org.gradle.testkit.fixtures.maven;

    public interface MavenRepository extends Repository {
        MavenModule module(String groupId, String artifactId);
        MavenModule module(String groupId, String artifactId, String version);
    }

    public interface MavenModule extends Module {
        MavenModule publish();
        MavenModule dependsOn(MavenModule module);

        // getter methods for artifacts and coordinates
    }

    package org.gradle.testkit.fixtures.ivy;

    public interface IvyRepository extends Repository {
        IvyModule module(String organisation, String module);
        IvyModule module(String organisation, String module, String revision);
    }

    public interface IvyModule extends Module {
        IvyModule publish();
        IvyModule dependsOn(String organisation, String module, String revision);

        // getter methods for artifacts and coordinates
    }

The use of the test fixture in an integration test could look as such:

    class UserFunctionalTest extends FunctionalTest {
        MavenRepository mavenRepository
        MavenModule mavenModule

        def setup() {
            MavenRepository mavenRepository = new MavenFileRepository(new File('/tmp/gradle-build'))
            MavenModule mavenModule = mavenRepository.module('org.gradle', 'test', '1.0')
            mavenModule.publish()
        }

         def "can resolve dependency"() {
            given:
            buildFile << ""
            configurations {
                myConf
            }

            dependencies {
                myConf "${mavenModule.groupId}:${mavenModule.artifactId}:${mavenModule.version}"
            }

            repositories {
                maven {
                    url mavenRepository.uri.toURL()
                }
            }
            """

            when:
            def result = succeeds('dependencies')

            then:
            result.standardOutput.contains("""
            myConf
            \--- ${mavenModule.groupId}:${mavenModule.artifactId}:${mavenModule.version}
            """)
        }
    }

### Implementation

* A new module named `test-kit-fixtures` will be created in Gradle core.
* Default implementations for `IvyRepository`, `MavenRepository`, `IvyModule` and `MavenModule` will be file-based.
* Modules can depend on each other to model transitive dependency relations.

### Test coverage

* User can create an Ivy repository representation in a directory of choice.
* User can create a Maven repository representation in a directory of choice.
* The Maven repository instance allows for creating modules in the repository with the standard Maven structure.
* The Ivy repository instance allows for creating modules in the repository with the default Ivy structure.
* Module dependencies can be modeled. On resolving the top-level dependency transitive dependencies are resolved as well.

### Open issues

none

# Backlog

- Setting up a multi-project build should be easy. At the moment a user would have to do all the leg work. The work required could be simplified by introducing helper methods.
- Potentially when running under the plugin dev plugin, defer clean up of test daemons to some finalizer task
- Have test daemons reuse the artifact cache and other caches in ~/.gradle, and just ignore the configuration files there.
- More convenient construction of test project directories (i.e. something like Gradle core's test directory provider)
- Integration with IDE plugins e.g. for automatic setup of debug flag
- Automatic classpath injection for projects that do _not_ contain a plugin definition e.g. just a collection of custom task types. The plugin DSL would not be used in those
cases.
