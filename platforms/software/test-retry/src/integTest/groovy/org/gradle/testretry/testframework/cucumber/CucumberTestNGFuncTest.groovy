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

import spock.lang.PendingFeature

class CucumberTestNGFuncTest extends AbstractCucumberFuncTest {

    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'org.testng:testng:7.5'
                testImplementation 'io.cucumber:cucumber-testng:7.0.0'
            }
            
            test.useTestNG()
        """
    }

    @PendingFeature
    def "retries scenarios independently from each other"() {
        given:
        writeFlakyFeatureFile()
        writeFlakyStepDefinitions()
        writeCucumberEntrypoint()

        when:
        succeeds('test')

        then:
        with(output) {
            assert it.count('runScenario[0]("Passing scenario", "Optional[Retry feature]") PASSED') == 1
            assert it.count('runScenario[1]("Flaky scenario", "Optional[Retry feature]") FAILED') == 1
            assert it.count('runScenario[1]("Flaky scenario", "Optional[Retry feature]") PASSED') == 1
        }
    }

    private writeCucumberEntrypoint() {
        writeJavaTestSource """
            package acme;

            import io.cucumber.testng.AbstractTestNGCucumberTests;
            import io.cucumber.testng.CucumberOptions;
            import org.testng.annotations.Test;
            
            @CucumberOptions(
                features = "src/test/resources/features",
                glue = "acme"
            )
            @Test
            public class RetryFeatureTest extends AbstractTestNGCucumberTests {
            }
        """
    }
}
