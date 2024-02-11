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

package org.gradle.java.fixtures

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Verifies that we can use test fixtures without applying other plugins. This is
 * important for integration with other ecosystems, for example, if a Kotlin or
 * Android project wants to use Java test fixtures without applying the Java plugin.
 */
class StandaloneTestFixturesIntegrationTest extends AbstractIntegrationSpec {

    def "can compile test fixtures"() {
        buildFile << """
            plugins {
                id 'java-test-fixtures'
            }

            task verify {
                dependsOn tasks.compileTestFixturesJava
                def files = sourceSets.testFixtures.output.classesDirs
                doLast {
                    assert files.singleFile.listFiles()*.name == ['Example.class']
                }
            }
        """
        file("src/testFixtures/java/Example.java") << """
            class Example {}
        """

        expect:
        succeeds("verify")
    }

    def "can depend on test fixtures from another project"() {
        buildFile << """
            plugins {
                id 'java-test-fixtures'
            }
        """
        file("src/testFixtures/java/Example.java") << """
            class Example {
                int getNumber() {
                    return 42;
                }
            }
        """

        settingsFile << "include 'consumer'"
        file("consumer/build.gradle") << """
            plugins {
                id 'java-library'
            }
            ${mavenCentralRepository()}
            testing.suites.test {
                useJUnitJupiter()
                dependencies {
                    implementation testFixtures(project(':'))
                }
            }
        """

        file("consumer/src/test/java/ExampleTest.java") << """
            public class ExampleTest {
                @org.junit.jupiter.api.Test
                public void test() {
                    assert new Example().getNumber() == 42;
                }
            }
        """

        expect:
        succeeds(":consumer:test")
    }
}
