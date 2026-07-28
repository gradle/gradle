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
package org.gradle.testretry

class KotlinDslFuncTest extends AbstractPluginFuncTest {
    @Override
    String getLanguagePlugin() {
        return 'java'
    }

    def "kotlin extension configuration (gradle version #gradleVersion)"() {
        given:
        buildFile.delete()
        buildFile = testProjectDir.newFile('build.gradle.kts')
        buildFile.text = """
            plugins {
                java
                id("org.gradle.test-retry")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation("${junit4Dependency()}")
            }

            tasks.test {
                retry {
                    maxRetries.set(2)
                }
            }
        """

        // Adding a single test because Gradle 9 fails the test task if no tests are found.
        writeJavaTestSource(passingTest())

        expect:
        gradleRunner(gradleVersion).build()

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private static String passingTest() {
        """
            package acme;
            
            public class PassingTest {
                @org.junit.Test
                public void test() {
                    // Nothing to do
                }
            }
        """
    }
}
