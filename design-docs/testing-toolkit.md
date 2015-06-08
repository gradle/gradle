# Testing custom build logic

This stories are partially extracted, re-aligned and distilled from this [spec](https://github.com/gradle/gradle/blob/master/design-docs/testing-user-build-logic.md).

## High-level goals

* Writing and executing integration tests with `Project` instance that behaves similar to real-world object (aka project created by `ProjectBuilder`).
* Writing and executing functional tests against build scripts via Tooling API.
* Providing utility methods for common operations in tests e.g. creating directories/files.
* Generating Ivy and Maven repositories for testing purposes. Creating modules and artifacts in a repository to emulate dependency management behavior.

The test-kit will be agnostic of the test framework preferred by the user (e.g. JUnit, TestNG, Spock). Adapters will be provided to make it easy to integrate the test-kit with a specific test framework.

## Technical details

* Except for the Spock test adapter all code will be developed in Java.
* The test-kit and all test adapters are implemented in the same repository as Gradle core. The test-kit classes are shipped with Gradle core distribution and can be imported as needed.
* The artifacts for test-kit and test adapters are published to a central repository (likely our own repository). Publishing Gradle core should trigger publishing the test-kit/test adapters.
* The build of the project will depends on the latest Gradle version. The version will need to updated manually in the beginning. We could also think of an automated solution here that uses the latest
nightly.

## Story 1: User creates and executes a functional test using the test-kit

A set of interfaces/builders will be developed to provide programmatic execution of Gradle builds. Tests are executed with the Tooling API.

### User visible changes

The test-kit does not have an opinion about the used test framework. It's up to the user to choose a test framework and declare its dependency.

    dependencies {
        // Declare the preferred test framework
        testCompile '...'
    }

As a user, you write your functional test by using the following interfaces:

    package org.gradle.testkit.functional;

    public interface GradleRunner {
        File getWorkingDir();
        void setWorkingDir(File workingDirectory);
        List<String> getArguments();
        void setArguments(List<String> string);
        void useTasks(List<String> taskNames);
        BuildResult succeeds();
        BuildResult fails();
    }

    public interface BuildResult {
        String getStandardOutput();
        String getStandardError();
        List<String> getExecutedTasks();
        List<String> getSkippedTasks();
    }

    public class GradleRunnerFactory {
        public static GradleRunner create() { /* ... */ }
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

            when:
            def result = GradleRunnerFactory.create().with {
                workingDir = testProjectDir
                arguments << "helloWorld"
                succeed()
            }

            then:
            result.standardOutput.contains "Hello World!"
        }
    }

### Implementation

* A new module named `test-kit-functional` will be created in Gradle core.
* The implementation will be backed by the Tooling API.
* The Gradle version/distribution selected will be what is selected by the Tooling APIs default behaviour (i.e. at this point, this is not specifiable).
* No environmental control will be allowed (e.g. setting env vars or sys props).

### Test coverage

* Execute a build that is expected to succeed.
    * No exception is thrown from `GradleRunner`.
    * Standard output retrieved from `GradleRunner` contains `println` or log message.
* Execute a build that is expected to succeed but fails.
    * A `UnexpectedBuildFailure` is thrown.
* Execute a build that is expected to fail.
    * No exception is thrown from `GradleRunner`.
    * Standard error retrieved from `GradleRunner` contains error message.
* Execute a build that is expected to fail but succeeds.
    * A `UnexpectedBuildSuccess` is thrown.
* A build can be executed with more than one task.
    * Tasks that are marked SKIPPED, UP-TO-DATE or FAILED can be retrieved as skipped tasks from `GradleRunner`.
    * All successful, failed or skipped tasks can be retrieved as executed tasks from `GradleRunner`.
* A build can be provided with command line arguments. Upon execution the provided options come into effect.
* A build that has `buildSrc` project does not list executed tasks from that project when retrieved from `GradleRunner`.
* The classpath of the tooling API execution is set up properly.
    * Class files of project sources under test (e.g. plugin classes) are added to classpath of the tooling API execution.
        * A plugin class under test can be referenced and tested in build script.
        * A custom task class under test can be referenced and tested in build script.
        * Arbitrary classes under test can be referenced and tested in build script.
    * Classes originating from external libraries used by classes under tests are added to classpath of the tooling API execution.
* Tooling API mechanical failures produce good diagnostic messages.

### Open issues

* The Tooling API executes the test in a Daemon JVM. Debugging in the IDE won't work until we allow for executing the tests in the same JVM process. Alternative: Remote debugging
* The daemon is known to still have issues. What should happen if one of the daemon crashes or misbehaves?
* Setting up a multi-project build should be easy. At the moment a user would have to do all the leg work. In a later story the work required could be simplified by introducing helper methods.
* Should we allow for executing a build with a different Gradle version and/or distribution at this point? This is important for organizations that create their own distribution. In the Gradle core
build the Gradle distribution should be the one built locally from the sources (same as the referenced by `IntegrationTestBuildContext.getGradleHomeDir()`).

## Story 2: JUnit test adapter

An abstract class that simplifies the use of the test-kit through the test framework JUnit.

### User visible changes

A user will need to declare the dependency on JUnit in the build script.

    dependencies {
        testCompile 'junit:junit:4.8.2'
    }

As a user, you write your functional test by extending the base class `FunctionalTest`.

    import org.gradle.testkit.functional.junit.FunctionalTest;

    import java.util.List;
    import java.util.ArrayList;
    import org.junit.Test;
    import static org.junit.Assert.assertTrue;

    public class UserFunctionalTest extends FunctionalTest {
        @Test
        public void canExecuteBuildFileUsingTheJavaPlugin() {
            writeToFile(getBuildFile(), "apply plugin: 'java'");
            BuildResult result = succeeds("build")
            List<String> expectedTaskNames = new ArrayList<String>();
            expectedTaskNames.add("classes");
            expectedTaskNames.add("test");
            expectedTaskNames.add("check");
            assertTrue(result.getExecutedTasks().containsAll(expectedTaskNames));
        }
    }

### Implementation

* A new module named `test-kit-junit` will be created in Gradle core.
* Temporary directories for test execution per test case will be creates by using the JUnit Rule [TemporaryFolder](http://junit.org/apidocs/org/junit/rules/TemporaryFolder.html).
* The functional test implementation uses the `GradleRunner` and provides methods to simplify the creation of tests with JUnit.

The base class implementation could look similar to the following code snippet:

    package org.gradle.testkit.functional.junit;

    import org.gradle.testkit.functional.*;

    import java.io.File;
    import org.junit.Before;
    import org.junit.Rule;
    import org.junit.rules.TemporaryFolder;

    public abstract class FunctionalTest {
        @Rule private final TemporaryFolder temporaryFolder = new TemporaryFolder("build/tmp/test-files");
        private File testDirectory;
        private GradleRunner gradleRunner;

        @Before
        public void setup() {
            testDirectory = temporaryFolder.newFolder("...");
            gradleRunner = GradleRunnerFactory.create();
            gradleRunner.setWorkingDir(testDirectory);
        }

        protected GradleRunner getGradleRunner() {
            return gradleRunner;
        }

        protected File getTestDirectory() {
            return testDirectory;
        }

        protected File getBuildFile() {
            return new File(testDirectory, "build.gradle");
        }

        protected File getSettingsFile() {
            return new File(testDirectory, "settings.gradle");
        }

        protected BuildResult succeeds(String... tasks) {
            return gradleRunner.useTasks(Arrays.asList(tasks)).succeeds();
        }

        protected BuildResult fails(String... tasks) {
            return gradleRunner.useTasks(Arrays.asList(tasks)).fails();
        }

        protected void writeToFile(File file, String text) {
            ...
        }
    }

### Test coverage

* Users can use `FunctionalTest` to write their own functional tests and successfully execute them.
* Users can create new files and directories with the JUnit Rule accessible from the base class.
* Users can create an assertion on whether tasks should be executed successfully and whether the execution should fail.
* Each test method creates a new temporary directory. This temporary test directory is not deleted after test execution.
* A user can create temporary files and directories and files with the provided test rule.
* A user can configure the `GradleRunner` e.g. to add arguments.
* Test methods can write a `build.gradle` and `settings.gradle` file.

### Open issues

* Potentially expose test fixtures as Gradle plugins so resources can be set up/clean automatically for each test case.

## Story 3: Fine-tuning test execution behavior

The first two stories set up the basic mechanics for the test-kit. To make the test-kit production-ready these mechanics need to be fine-tuned.

### User visible changes

The `GradleRunner` interface will be extended to provide additional methods.

    public interface GradleRunner {
        boolean isDebug();
        void enableDebug();
    }

### Implementation

* After all tests of a project are executed, temporary test files are automatically cleaned up.
* Daemon instances run isolated from other tests and the user's `~/.gradle` directory.
* Setting appropriate default daemon JVM arguments for test execution.
* Daemon process is started with remote debugging JVM parameters.

### Test coverage

* After test execution, temporary test files are deleted independent of the number of exercised tests, or whether the result is successful or failed.
* Test are executed in dedicated, isolate daemon instance.
    * If no daemon process exists, create a new one and use it. Regular Gradle build executions will not use the daemon process dedicated for test execution.
    * If a daemon process already exists, determine if it is a daemon process dedicated for test execution. Reuse it if possible. Otherwise, create a new one.
    * A Daemon process dedicated for test execution only use its dedicated JVM parameters. Any configuration found under `~/.gradle` is not taken into account.
* A user can start the `GradleRunner` with remote debugging JVM parameter for debugging purposes. By default the `GradleRunner` does not use the debugging JVM parameters.

### Open issues

none

## Story 4: Groovy/Spock framework test adapter

A Groovy bean that can be mixed in with tests classes written in Groovy or test classes that use the Spock framework.

### User visible changes

A Groovy-based test only requires declaring a dependency on Groovy.

    dependencies {
        testCompile 'org.codehaus.groovy:groovy:2.4.3'
    }

As a user, you write your Groovy functional test by extending `groovy.util.GroovyTestCase` and implementing the functional test trait.

    import org.gradle.testkit.functional.groovy.FunctionalTest

    import groovy.util.GroovyTestCase

    class UserFunctionalTest extends GroovyTestCase implements FunctionalTest {
        void canExecuteBuildFileUsingJavaPlugin() {
            buildFile << "apply plugin: 'java'"
            def result = succeeds('build')
            assert result.executedTasks.containsAll(['classes', 'test', 'check']);
        }
    }

A Spock-based test requires the declaration of a dependency on the Spock framework.

    dependencies {
        testCompile 'org.spockframework:spock-core:1.0-groovy-2.4'
    }

As a user, you write your Spock functional test by extending `spock.lang.Specification` and implementing the functional test trait.

    import org.gradle.testkit.functional.groovy.FunctionalTest

    import spock.lang.Specification

    class UserFunctionalTest extends Specification implements FunctionalTest {
        def "can execute build file using the Java plugin"() {
            given:
            buildFile << "apply plugin: 'java'"

            when:
            def result = succeeds('build')

            then:
            result.executedTasks.containsAll(['classes', 'test', 'check']);
        }
    }

### Implementation

* A new module named `test-kit-groovy` will be created in Gradle core.
* The functional test implementation uses the `GradleRunner` and provides methods to simplify the creation of tests with Groovy or Spock.

The implementation of functional test trait could look similar to the following class:

    package org.gradle.testkit.functional.spock

    import org.gradle.testkit.functional.*

    import java.io.File

    trait FunctionalTest {
        private File testDirectory
        private final GradleRunner gradleRunner = GradleRunnerFactory.create()

        void setup() {
            testDirectory = ... // create test directory
            gradleRunner.setWorkingDir(testDirectory)
        }

        GradleRunner getGradleRunner() {
            gradleRunner
        }

        File getTestDirectory() {
            testDirectory
        }

        File getBuildFile() {
            new File(testDirectory, "build.gradle")
        }

        File getSettingsFile() {
            new File(testDirectory, "settings.gradle")
        }

        BuildResult succeeds(String... tasks) {
            gradleRunner.useTasks(Arrays.asList(tasks)).succeeds()
        }

        BuildResult fails(String... tasks) {
            gradleRunner.useTasks(Arrays.asList(tasks)).fails()
        }
    }

### Test coverage

* Users can use `FunctionalTest` to write their own functional tests with either `GroovyTestCase` or Spock and successfully execute them.
* Users can create new files and directories with the help of the mixed in functional test Groovy class.
* Users can create an assertion on whether tasks should be executed successfully and whether the execution should fail.
* Each test method creates a new temporary directory. This temporary test directory is not deleted after test execution.
* A user can create temporary files and directories and files with the provided test rule.
* A user can configure the `GradleRunner` e.g. to add arguments.
* Test methods can write a `build.gradle` and `settings.gradle` file.

### Open issues

* Instead of using a Groovy trait should we go for a different solution as it required Groovy >= 2.3?

## Story 5: User creates and executes an integration test using the test-kit

A set of interfaces/builders will be developed to provide programmatic creation of a dummy `Project` instance.

### User visible changes

As a user, you write your integration test by using the following interfaces:

    package org.gradle.testkit.integration;

    public interface GradleProjectBuilder {
        File getWorkingDir();
        void setWorkingDir(File directory);

        String getName();
        void setName(String projectName);

        Project getParent();
        void setParent(Project parentProject);

        Project build();
    }

    public class GradleProjectBuilderFactory {
        public static GradleProjectBuilder create() { /* ... */ }
    }

A integration test using Spock could look as such:

    class UserIntegrationTest extends Specification {
        def "run build"() {
            given:
            def project = GradleProjectBuilderFactory.create().with {
                workingDir = new File("/tmp/gradle-build")
                build()
            }

            when:
            project.plugins.apply('java')

            then:
            project.tasks.getByName('classes')
            project.tasks.getByName('test')
            project.tasks.getByName('check')
        }
    }

### Implementation

* A new module named `test-kit-integration` will be created in Gradle core.
* The actual implementation of the dummy project creation is hidden from the user. As a start we can reuse the `ProjectBuilder`. Later this implementation can be swapped out.
* Provide base classes for all test adapters.
* The Tooling API is not involved.

### Test coverage

* A project can be created.
* Projects can form project hierarchies to model multi-project builds.
* Methods on a project instance can be called as if it would be a regular project instance. There are some limitation though.

### Open issues

* A user should be allowed to execute a task with a public method. Calling `Task.execute()` is sufficient but is an internal API. It's easier to test custom task types.
* A user should be allowed to evaluate a project to trigger lifecycle events. Calling `Project.evaluate()` works but is an internal API.
* Where do we draw the line between dummy project and real project instance?

## Story 6: User can create repositories and populate dependencies

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

## Story 7: Integration with plugin development plugin

### User visible changes

### Implementation

### Test coverage

### Open issues

## Story 8: Test build code against different Gradle versions

Extend the capabilities of `GradleRunner` to allow for testing a build against more than one Gradle version. The typical use case is to check the runtime compatibility of build logic against a
specific Gradle version. Example: Plugin X is built with 2.3, but check if it is also compatible with 2.2, 2.4 and 2.5.

### User visible changes

A user interacts with the following interfaces/classes:

    package org.gradle.testkit.functional.dist;

    public interface GradleDistribution<T> {
        T getHandle();
    }

    public final class VersionBasedGradleDistribution implements GradleDistribution<String> { /* ... */ }
    public final class URILocatedGradleDistribution implements GradleDistribution<URI> { /* ... */ }
    public final class InstalledGradleDistribution implements GradleDistribution<File> { /* ... */ }

    package org.gradle.testkit.functional;

    public class GradleRunnerFactory {
        public static GradleRunner create(GradleDistribution gradleDistribution) { /* ... */ }
    }

A functional test using Spock could look as such:

    class UserFunctionalTest extends Specification {
        @Unroll
        def "run build with #gradleDistribution"() {
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

            when:
            def result = GradleRunnerFactory.create(gradleDistribution).with {
                workingDir = testProjectDir
                arguments << "helloWorld"
                succeed()
            }

            then:
            result.standardOutput.contains "Hello World!"

            where:
            gradleDistribution << [new VersionBasedGradleDistribution("2.4"), new VersionBasedGradleDistribution("2.5")]
        }
    }

### Implementation

* The tooling API uses the provided Gradle distribution. Any of the following locations is valid:
    * Gradle version String e.g. `"2.4"`
    * Gradle URI e.g. `new URI("http://services.gradle.org/distributions/gradle-2.4-bin.zip")`
    * Gradle installation e.g. `new File("/Users/foo/bar/gradle-installation/gradle-2.4-bin")`
* Each test executed with a specific Gradle version creates a unique temporary test directory.
* Tests executed with the different Gradle versions run with an isolated daemon.

### Test coverage

* `GradleRunnerFactory` throws and exception if `GradleDistribution` is provided that doesn't match the supported types.
* A test can be executed with Gradle distribution provided by the user. The version of the distribution can be a different from the Gradle version used to build the project.

### Open issues

* Execution of tests in parallel for multiple Gradle versions
* JUnit Runner implementation to simplify definition of Gradle distributions
