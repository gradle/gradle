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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.TargetCoverage

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT4_CATEGORIES

@TargetCoverage({ JUNIT4_CATEGORIES })
class JUnit4TestTaskIntegrationTest extends AbstractJUnit4TestTaskIntegrationTest implements JUnit4MultiVersionTest {
    def "options can be set prior to setting same test framework for the default test task"() {
        given:
        file('src/test/java/MyTest.java') << standaloneTestClass
        file("src/test/java/Slow.java") << """public interface Slow {}"""

        settingsFile << "rootProject.name = 'Sample'"
        buildFile << """apply plugin: 'java'
            test {
                options {
                    excludeCategories = ["Slow"]
                }
                $configureTestFramework
            }
        """.stripIndent()

        expect:
        succeeds("test")
    }

    def "options can be set prior to setting same test framework for a custom test task"() {
        given:
        file('src/customTest/java/MyTest.java') << standaloneTestClass

        settingsFile << "rootProject.name = 'Sample'"
        buildFile << """
            sourceSets {
                customTest
            }

            dependencies {
                customTestImplementation 'junit:junit:${version}'
            }

            tasks.create('customTest', Test) {
                classpath = sourceSets.customTest.runtimeClasspath
                testClassesDirs = sourceSets.customTest.output.classesDirs
                options {
                    excludeCategories = ["MyTest\\\$Slow"]
                }
                $configureTestFramework
            }
        """.stripIndent()

        expect:
        succeeds("customTest")
    }
}
