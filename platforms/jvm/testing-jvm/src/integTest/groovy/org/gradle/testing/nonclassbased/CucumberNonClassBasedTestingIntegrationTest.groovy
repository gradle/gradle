/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.nonclassbased

import groovy.xml.XmlSlurper
import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import static org.gradle.testing.nonclassbased.AbstractNonClassBasedTestingIntegrationTest.DEFAULT_DEFINITIONS_LOCATION

/**
 * Integration test to verify that Cucumber JVM feature files can be executed
 * without using non-class-based testing support via {@code @RunWith} or other JUnit annotations.
 */
class CucumberNonClassBasedTestingIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    def "can run Cucumber JVM feature files without using non-class-based testing (features in: #testLocation)"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()

                dependencies {
                    implementation("io.cucumber:cucumber-java:7.15.0")
                    runtimeOnly("io.cucumber:cucumber-junit-platform-engine:7.15.0")
                }

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("$testLocation")
                    }
                }
            }
        """

        writeCucumberFeatureFiles(testLocation)
        writeCucumberStepDefinitions()

        when:
        succeeds("test")

        then:
        def result = resultsFor()
        result.testPath(":$testLocation/helloworld.feature:Say hello /two/three").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)

        where:
        testLocation << ["src/test/resources", DEFAULT_DEFINITIONS_LOCATION]
    }

    def "can run Cucumber tests with a custom test workingDir and report names are relative to project dir"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            File customWorkingDir = file("custom/WorkingDir")
            customWorkingDir.mkdirs()

            testing.suites.test {
                useJUnitJupiter()

                dependencies {
                    implementation("io.cucumber:cucumber-java:7.15.0")
                    implementation("io.cucumber:cucumber-junit-platform-engine:7.15.0")
                }

                targets.all {
                    testTask.configure {

                        workingDir = customWorkingDir
                        testDefinitionDirs.from("src/test/resources")
                    }
                }
            }
        """

        writeCucumberFeatureFiles()
        writeCucumberStepDefinitions()

        when:
        succeeds("test")

        then:
        def result = resultsFor()
        result.testPath(":src/test/resources/helloworld.feature:Say hello /two/three").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }

    @Issue("https://github.com/gradle/gradle/issues/37850")
    def "Cucumber scenario class name in JUnit XML is the feature file path so it can drive test-retry filters"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()

                dependencies {
                    implementation("io.cucumber:cucumber-java:7.15.0")
                    runtimeOnly("io.cucumber:cucumber-junit-platform-engine:7.15.0")
                }

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("src/test/resources")
                    }
                }
            }
        """

        writeCucumberFeatureFiles()
        writeCucumberStepDefinitions()

        when:
        succeeds("test")

        then:
        // Every testcase in the JUnit XML must have a non-empty `classname`
        // attribute.  Pre-fix this was `JUnitPlatformSupport.NON_CLASS` = "", which broke Build
        // Scan reporting and the test-retry plugin's filter contract.
        def xml = new XmlSlurper().parse(file("build/test-results/test/TEST-helloworld.feature.xml"))
        def classnames = xml.testcase.collect { it.@classname.text() }
        assert !classnames.isEmpty()
        assert classnames.every { it == "helloworld.feature" }
    }

    @Issue("https://github.com/gradle/gradle/issues/37850")
    def "test-retry plugin can filter a failed Cucumber scenario by class+method name"() {
        given:
        settingsFile << """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                }
            }
        """
        buildFile << """
            plugins {
                id 'java'
                id 'org.gradle.test-retry' version '1.6.4'
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()

                dependencies {
                    implementation("io.cucumber:cucumber-java:7.15.0")
                    runtimeOnly("io.cucumber:cucumber-junit-platform-engine:7.15.0")
                }

                targets.all {
                    testTask.configure {
                        testDefinitionDirs.from("src/test/resources")
                        retry {
                            maxRetries = 1
                        }
                    }
                }
            }
        """

        writeCucumberFeatureFiles()
        writeAlwaysFailingCucumberStepDefinitions()

        when:
        // The scenario always fails; we expect the build to fail. The point of this test is to verify
        // that the test-retry plugin can *identify* the failed scenario (className non-empty) and
        // call TestFilter.includeTest() without tripping DefaultTestFilter.validateName.
        // Before the fix, this would throw "Selected test name cannot be null or empty." on the
        // attempted retry, masking the actual scenario failure.
        fails("test")

        then:
        // The retry plugin successfully formed a className+methodName pair from the descriptor —
        // the scenario name shows up in the retry plugin's output, proving the className contract.
        failure.assertHasErrorOutput("helloworld.feature")
        failure.assertHasErrorOutput("Say hello /two/three")
        // Pre-fix regression marker; must not appear.
        failure.assertNotOutput("Selected test name cannot be null or empty")
    }

    private writeAlwaysFailingCucumberStepDefinitions(String path = "src/test/java") {
        def stepDefFile = file("$path/HelloStepdefs.java")
        stepDefFile.parentFile.mkdirs()

        stepDefFile.text = """
            import io.cucumber.java.en.Given;
            import io.cucumber.java.en.Then;
            import io.cucumber.java.en.When;

            public class HelloStepdefs {
                @Given("^I have a hello app with Howdy and /four")
                public void I_have_a_hello_app_with() { }

                @When("^I ask it to say hi and /five/six/seven")
                public void I_ask_it_to_say_hi() { }

                @Then("^it should answer with Howdy World")
                public void it_should_answer_with() {
                    throw new AssertionError("Intentional failure to trigger test-retry");
                }
            }
        """
    }

private writeCucumberStepDefinitions(String path = "src/test/java") {
        def stepDefFile = file("$path/HelloStepdefs.java")
        stepDefFile.parentFile.mkdirs()

        stepDefFile.text = """
            import io.cucumber.java.en.Given;
            import io.cucumber.java.en.Then;
            import io.cucumber.java.en.When;

            public class HelloStepdefs {
                @Given("^I have a hello app with Howdy and /four")
                public void I_have_a_hello_app_with() {
                    System.out.println("Given");
                }

                @When("^I ask it to say hi and /five/six/seven")
                public void I_ask_it_to_say_hi() {
                    System.out.println("When");
                }

                @Then("^it should answer with Howdy World")
                public void it_should_answer_with() {
                    System.out.println("Then");
                }
            }
        """
    }

    private writeCucumberFeatureFiles(String path = "src/test/resources") {
        def featureFile = file("$path/helloworld.feature")
        featureFile.parentFile.mkdirs()

        featureFile.text = """
            Feature: Hello World /one

            @bar
            Scenario: Say hello /two/three
            Given I have a hello app with Howdy and /four
            When I ask it to say hi and /five/six/seven
            Then it should answer with Howdy World
        """
    }
}
