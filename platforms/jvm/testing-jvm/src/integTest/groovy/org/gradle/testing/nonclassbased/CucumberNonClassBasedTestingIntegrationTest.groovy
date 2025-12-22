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

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.testing.nonclassbased.AbstractNonClassBasedTestingIntegrationTest.DEFAULT_DEFINITIONS_LOCATION

/**
 * Integration test to verify that Cucumber JVM feature files can be executed
 * without using non-class-based testing support via {@code @RunWith} or other JUnit annotations.
 */
class CucumberNonClassBasedTestingIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.CUCUMBER
    }

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
        result.testPathPreNormalized(":$testLocation/helloworld.feature:Say hello /two/three").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)

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
        result.testPathPreNormalized(":src/test/resources/helloworld.feature:Say hello /two/three").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
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
