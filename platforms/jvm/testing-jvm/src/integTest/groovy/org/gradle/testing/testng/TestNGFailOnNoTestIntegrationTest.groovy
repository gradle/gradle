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

package org.gradle.testing.testng

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION
import static org.gradle.testing.fixture.TestNGCoverage.NEWEST

class TestNGFailOnNoTestIntegrationTest extends TestNGTestFrameworkIntegrationTest {

    static final String LATEST_TESTNG_VERSION = NEWEST

    def "test source and test task use same test framework"() {
        given:
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.testng:testng:$LATEST_TESTNG_VERSION'
            }
            test {
                useTestNG()
            }
        """

        file("src/test/java/NotATest.java") << """
            // missing @org.testng.annotations.Test
            public class NotATest {}
        """

        executer.expectDocumentedDeprecationWarning("No test executed. This behavior has been deprecated. " +
            "This will fail with an error in Gradle 9.0. There are test sources present but no test was executed. Please check your test configuration. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_fail_on_no_test_executed")

        expect:
        succeeds('test')
    }

    def "test source and test task use different test frameworks"() {
        given:
        buildFile << """
            apply plugin:'java-library'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${LATEST_JUPITER_VERSION}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            test {
                useJUnitPlatform()
            }
        """

        createPassingFailingTest()

        executer.expectDocumentedDeprecationWarning("No test executed. This behavior has been deprecated. " +
            "This will fail with an error in Gradle 9.0. There are test sources present but no test was executed. Please check your test configuration. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_fail_on_no_test_executed")

        expect:
        succeeds('test')
    }
}
