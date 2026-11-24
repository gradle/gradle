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
 * Gradle selects only the suite class, so its feature files lie under none of the task's test definition
 * directories and the scenarios have no path to be matched by. They are matched by the enclosing suite
 * class instead, so selecting or excluding it selects or excludes them as a group.
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
                // Names every scenario that executes, so tests need not rely on reported test paths.
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
        assertRanScenarios("Say hello", "Say goodbye")
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
        assertDidNotRunAnyScenarios()
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
        assertDidNotRunAnyScenarios()
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
        assertRanScenarios("Say hello", "Say goodbye")
    }

    def "selecting an unrelated test excludes scenarios selected from a directory"() {
        given:
        directorySuite()

        when:
        succeeds("test", "--tests", "JupiterTest.someMethod")

        then:
        assertDidNotRunAnyScenarios()
        resultsFor().assertTestPathsExecuted(":JupiterTest:someMethod()")
    }

    /**
     * A suite's features must stay selectable by the suite class when the task also has test definition
     * directories configured for unrelated features.
     */
    def "scenarios selected from a directory survive a filter when unrelated definition dirs are configured"() {
        given:
        directorySuite()
        definitionDirFeature()

        when:
        succeeds("test", "--tests", "RunCukesTest")

        then: "matched by the suite class, having no path"
        assertRanScenarios("Say hello", "Say goodbye")

        and: "matched by path, which this filter does not name"
        outputDoesNotContain("${RAN_MARKER}Unrelated scenario")
    }

    /**
     * The path tier of the rule. Passes with or without the fallback, and guards the fallback breaking
     * path matching.
     * <p>
     * Does not assert the suite's scenarios stay out: {@code --tests "unrelated"} prunes
     * {@code RunCukesTest} before a worker sees it, so that would pass whatever the filters did.
     */
    def "a filter naming a definition dir feature selects it by path"() {
        given:
        directorySuite()
        definitionDirFeature()

        when:
        succeeds("test", "--tests", "unrelated")

        then:
        assertRanScenarios("Unrelated scenario")
    }

    /**
     * A feature under a configured test definition directory, so it is matched by path rather than by an
     * enclosing class.
     * <p>
     * The Cucumber engine must be included at the top level or the directory selector that
     * {@code testDefinitionDirs} contributes reaches no engine that reads {@code .feature} files and the
     * feature is silently never found. The suite's {@code @IncludeEngines} covers only its own discovery.
     */
    private definitionDirFeature() {
        file("src/test/definitions/unrelated.feature") << """
Feature: Unrelated

    Scenario: Unrelated scenario
        Given I have a hello app
        Then it should answer
"""
        buildFile << """
            test {
                useJUnitPlatform {
                    includeEngines("cucumber")
                }
                testDefinitionDirs.from("src/test/definitions")
            }
        """
    }

    /**
     * Features reached by classpath resource, the idiomatic way to point a suite at a Cucumber feature
     * directory. The descriptors carry a {@code ClasspathResourceSource}.
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
     * The same features reached by directory, giving the descriptors a {@code FileSource}. The directory
     * is the suite's own selector, not the task's, so these exercise {@code FilePathFilter}'s fallback.
     * <p>
     * Verified through the step definitions' marker rather than the reported test path: with no
     * {@code testDefinitionDirs} to relativize against, a file-based test is reported under its absolute
     * file URL, which differs per machine.
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

    private void assertRanScenarios(String... scenarioNames) {
        Arrays.asList(scenarioNames).each { outputContains("$RAN_MARKER$it") }
    }

    private void assertDidNotRunAnyScenarios() {
        outputDoesNotContain(RAN_MARKER)
    }
}
