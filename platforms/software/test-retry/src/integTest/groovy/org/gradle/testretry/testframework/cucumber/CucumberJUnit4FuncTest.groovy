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

class CucumberJUnit4FuncTest extends AbstractCucumberFuncTest {

    def setup() {
        buildFile << """
            dependencies {
                testImplementation 'io.cucumber:cucumber-junit:7.0.0'
            }
        """
    }

    def "retries scenarios independently from each other (gradle version #gradleVersion)"(String gradleVersion) {
        given:
        writeFlakyFeatureFile()
        writeFlakyStepDefinitions()
        writeCucumberEntrypoint()

        when:
        def runner = gradleRunner(gradleVersion as String)
        def result = runner.build()

        then:
        with(result.output) {
            it.count("Passing scenario PASSED") == 1
            it.count("Flaky scenario FAILED") == 1
            it.count("Flaky scenario PASSED") == 1
        }

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private writeCucumberEntrypoint() {
        writeJavaTestSource """
            package acme;

            @org.junit.runner.RunWith(io.cucumber.junit.Cucumber.class)
            @io.cucumber.junit.CucumberOptions(
                features = "src/test/resources/features",
                glue = "acme"
            )
            public class RetryFeatureTest {
            }
        """
    }
}
