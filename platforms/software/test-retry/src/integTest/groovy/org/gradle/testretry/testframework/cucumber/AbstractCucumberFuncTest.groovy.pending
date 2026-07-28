/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.testframework.cucumber

import org.gradle.testretry.AbstractFrameworkFuncTest

abstract class AbstractCucumberFuncTest extends AbstractFrameworkFuncTest {

    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'io.cucumber:cucumber-java:7.0.0'
            }
            
            test.retry.maxRetries = 1
        """
    }

    protected void writeFlakyFeatureFile() {
        writeFeatureFile("flaky_and_passing_scenarios", """
            Feature: Retry feature
              
              Scenario: Passing scenario
                Given a passing setup
                When a passing action
                Then a passing verification
            
              Scenario: Flaky scenario
                Given a passing setup
                When a passing action
                Then a flaky verification
        """)
    }

    protected void writeFeatureFile(String name, String content) {
        def featuresDir = "src/test/resources/features"
        new File(testProjectDir.root, featuresDir).mkdirs()
        testProjectDir.newFile("$featuresDir/${name}.feature") << content
    }

    protected void writeFlakyStepDefinitions() {
        writeJavaTestSource """
            package acme;

            import io.cucumber.java.en.Given;
            import io.cucumber.java.en.Then;
            import io.cucumber.java.en.When;

            public class RetryFeatureStepDefinitions {
                @Given("a passing setup")
                public void passingSetup() {
                }

                @When("a passing action")
                public void passingAction() {
                }

                @Then("a passing verification")
                public void passingVerification() {
                }

                @Then("a flaky verification")
                public void flakyVerification() {
                    ${flakyAssert("cucumber")}
                }
            }
        """
    }
}
