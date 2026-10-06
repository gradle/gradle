/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testing.cucumberjvm

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Filtering of Cucumber scenarios reached through a JUnit Platform {@code @Suite} entry point.
 * <p>
 * The suite class is the only thing Gradle selects, so the run has no directory selectors and
 * {@code FilePathFilter} is not registered. The feature and scenario descriptors below it carry a
 * {@code FileSource} or a {@code ClasspathResourceSource}, neither of which has a dedicated filter in
 * such a run, so they are judged by {@code ClassMethodNameFilter} against the suite class that encloses
 * them. Selecting or excluding the suite class therefore selects or excludes its scenarios as a group.
 */
class CucumberSuiteFilteringIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    private static final String RAN_MARKER = "RAN SCENARIO: "

    def setup() {
        buildFile << """
            plugins {
                id("java")
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
                testImplementation("org.junit.platform:junit-platform-suite-api:1.14.1")
                testImplementation(platform("io.cucumber:cucumber-bom:7.31.0"))
                testImplementation("io.cucumber:cucumber-java")
                testRuntimeOnly("io.cucumber:cucumber-junit-platform-engine")
                testRuntimeOnly("org.junit.platform:junit-platform-suite-engine")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            test {
                useJUnitPlatform {
                    // The scenarios must only be discovered through the suite, not a second time by the
                    // Cucumber engine scanning the classpath for itself.
                    includeEngines("junit-platform-suite", "junit-jupiter")
                }
                testLogging.showStandardStreams = true
            }
        """

        file("src/test/resources/features/helloworld.feature") << """
Feature: Hello World

    Scenario: Say hello
        Given I have a hello app
        Then it should answer
"""
        file("src/test/resources/features/goodbye.feature") << """
Feature: Goodbye World

    Scenario: Say goodbye
        Given I have a hello app
        Then it should answer
"""
        file("src/test/java/HelloStepdefs.java") << """
            import io.cucumber.java.Before;
            import io.cucumber.java.Scenario;
            import io.cucumber.java.en.Given;
            import io.cucumber.java.en.Then;

            public class HelloStepdefs {
                // Names every scenario that actually executes, so a test can verify execution without
                // depending on the name the scenario is reported under.
                @Before
                public void announce(Scenario scenario) {
                    System.out.println("$RAN_MARKER" + scenario.getName());
                }

                @Given("^I have a hello app")
                public void i_have_a_hello_app() {}

                @Then("^it should answer")
                public void it_should_answer() {}
            }
        """
        file("src/test/java/JupiterTest.java") << """
            import org.junit.jupiter.api.Test;

            public class JupiterTest {
                @Test
                public void someMethod() {}

                @Test
                public void otherMethod() {}
            }
        """
    }

    def "selecting the suite class runs its scenarios"() {
        given:
        classpathResourceSuite()

        when:
        succeeds("test", "--tests", "RunCukesTest")

        then:
        resultsFor().assertTestPathsExecuted(
            ":RunCukesTest:feature_classpath_features/goodbye.feature:Say goodbye",
            ":RunCukesTest:feature_classpath_features/helloworld.feature:Say hello",
        )
    }

    def "selecting an unrelated test excludes the suite's scenarios"() {
        given:
        classpathResourceSuite()

        when:
        succeeds("test", "--tests", "JupiterTest.someMethod")

        then:
        resultsFor().assertTestPathsExecuted(":JupiterTest:someMethod()")
    }

    def "excluding the suite class excludes its scenarios"() {
        given:
        classpathResourceSuite()
        buildFile << """
            test {
                filter {
                    excludeTestsMatching "RunCukesTest"
                }
            }
        """

        when:
        succeeds("test")

        then:
        resultsFor().assertTestPathsExecuted(
            ":JupiterTest:someMethod()",
            ":JupiterTest:otherMethod()",
        )
    }

    def "selecting the suite class runs scenarios selected from a directory"() {
        given:
        directorySuite()

        when:
        succeeds("test", "--tests", "RunCukesTest")

        then:
        outputContains("${RAN_MARKER}Say hello")
        outputContains("${RAN_MARKER}Say goodbye")
    }

    def "selecting an unrelated test excludes scenarios selected from a directory"() {
        given:
        directorySuite()

        when:
        succeeds("test", "--tests", "JupiterTest.someMethod")

        then:
        outputDoesNotContain(RAN_MARKER)
        resultsFor().assertTestPathsExecuted(":JupiterTest:someMethod()")
    }

    /**
     * A file-based test selected by a class-based entry point must not be excluded just because the task
     * also has test definition directories configured for unrelated features. Its path lies under none
     * of them, so it cannot be matched by path, and the enclosing suite class decides instead.
     */
    def "scenarios selected from a directory survive a filter when unrelated definition dirs are configured"() {
        given:
        directorySuite()
        file("src/test/definitions/unrelated.feature") << """
Feature: Unrelated

    Scenario: Unrelated scenario
        Given I have a hello app
        Then it should answer
"""
        buildFile << """
            test {
                testDefinitionDirs.from("src/test/definitions")
            }
        """

        when:
        succeeds("test", "--tests", "RunCukesTest")

        then:
        outputContains("${RAN_MARKER}Say hello")
        outputContains("${RAN_MARKER}Say goodbye")

        and: "the features under the definition dirs are matched by path, and this filter names no path"
        outputDoesNotContain("${RAN_MARKER}Unrelated scenario")
    }

    /**
     * Features reached by classpath resource, as {@code @SelectClasspathResource} is the idiomatic way to
     * point a suite at a Cucumber feature directory. The descriptors carry a {@code ClasspathResourceSource}.
     */
    private classpathResourceSuite() {
        file("src/test/java/RunCukesTest.java") << """
            import org.junit.platform.suite.api.IncludeEngines;
            import org.junit.platform.suite.api.SelectClasspathResource;
            import org.junit.platform.suite.api.Suite;

            @Suite
            @IncludeEngines("cucumber")
            @SelectClasspathResource("/features")
            public class RunCukesTest {
            }
        """
    }

    /**
     * The same features reached by directory instead, which is what gives the descriptors a
     * {@code FileSource}. The directory is the suite's own selector, not the test task's, so the run
     * still has no directory selectors and {@code FilePathFilter} is still absent.
     * <p>
     * Scenarios reached this way are verified through the marker the step definitions print rather than
     * through their reported test path. A file-based test is reported under its path relative to the
     * task's {@code testDefinitionDirs}, and a class-based run has none to relativize against, so the
     * name falls back to the absolute file URL of the feature, which differs on every machine.
     * Configuring {@code testDefinitionDirs} to make it relative would add the directory selectors this
     * test exists to do without.
     */
    private directorySuite() {
        file("src/test/java/RunCukesTest.java") << """
            import org.junit.platform.suite.api.IncludeEngines;
            import org.junit.platform.suite.api.SelectDirectories;
            import org.junit.platform.suite.api.Suite;

            @Suite
            @IncludeEngines("cucumber")
            @SelectDirectories("src/test/resources/features")
            public class RunCukesTest {
            }
        """
    }
}
