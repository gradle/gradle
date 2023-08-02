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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.testing.fixture.JUnitCoverage.getLATEST_JUPITER_VERSION

class TestTaskFailOnNoTestIntegrationTest extends AbstractIntegrationSpec {

    def "test succeeds if a test was executed"() {
        createBuildFileWithJUnitJupiter()

        file("src/test/java/SomeTest.java") << """
            public class SomeTest {
                @org.junit.jupiter.api.Test
                public void foo() { }
            }
        """

        expect:
        succeeds("test")
    }

    def "test succeeds with warning if no test was executed"() {
        createBuildFileWithJUnitJupiter()

        file("src/test/java/NotATest.java") << """
            public class NotATest {}
        """

        executer.expectDocumentedDeprecationWarning("No test executed. This behavior has been deprecated. " +
            "This will fail with an error in Gradle 9.0. There are test sources present but no test was executed. Please check your test configuration. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#test_task_fail_on_no_test_executed")

        expect:
        succeeds("test")
    }

    def "test is skipped if no test source detected"() {
        buildFile << "apply plugin: 'java'"

        file("src/test/java/not_a_test.txt")

        when:
        succeeds("test")

        then:
        skipped(":test")
    }

    def createBuildFileWithJUnitJupiter() {
        buildFile << """
            plugins {
                id 'java'
                id 'jvm-test-suite'
            }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${LATEST_JUPITER_VERSION}'
            }
            testing.suites.test {
                useJUnitJupiter()
            }
        """.stripIndent()
    }
}
