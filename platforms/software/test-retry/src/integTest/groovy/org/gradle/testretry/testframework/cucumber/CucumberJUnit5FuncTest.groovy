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

class CucumberJUnit5FuncTest extends AbstractCucumberFuncTest {

    def setup() {
        buildFile.text = """\
            plugins {
                id("com.gradle.cucumber.companion") version "1.0.1"
            }
            ${buildFile.text}
        """
        buildFile << """
            dependencies {
                testImplementation platform('org.junit:junit-bom:5.10.2')
                testImplementation 'io.cucumber:cucumber-java:7.0.0'
                testImplementation 'io.cucumber:cucumber-junit-platform-engine:7.0.0'
                testImplementation 'org.junit.jupiter:junit-jupiter'
                testImplementation 'org.junit.platform:junit-platform-suite'
            }
            
            test.useJUnitPlatform()
        """
    }

    @PendingFeature
    def "retries scenarios independently from each other (gradle version #gradleVersion)"(String gradleVersion) {
        given:
        writeFlakyFeatureFile()
        writeFlakyStepDefinitions()

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
}
