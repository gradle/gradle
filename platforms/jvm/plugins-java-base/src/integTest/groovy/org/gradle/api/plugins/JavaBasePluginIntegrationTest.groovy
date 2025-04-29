/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaBasePluginIntegrationTest extends AbstractIntegrationSpec {

    def "can define and build a source set with implementation dependencies"() {
        settingsFile << """
            include 'main', 'tests'
        """
        buildFile("main/build.gradle", """
            apply plugin: 'java'
        """)
        buildFile("tests/build.gradle", """
            apply plugin: 'java-base'
                sourceSets {
                    unitTest {
                    }
                }
                dependencies {
                    unitTestImplementation project(':main')
                }
        """)
        file("main/src/main/java/Main.java") << """public class Main { }"""
        file("tests/src/unitTest/java/Test.java") << """public class Test { Main main = null; }"""

        expect:
        succeeds(":test:unitTestClasses")
        file("main/build/classes/java/main").assertHasDescendants("Main.class")
        file("tests/build/classes/java/unitTest").assertHasDescendants("Test.class")
    }

    def "calling withSourcesJar before a component is available works"() {
        given:
        buildFile << """
            plugins {
                id 'java-base'
            }
            java {
                withSourcesJar()
            }
            apply plugin: 'java-library'
        """

        expect:
        succeeds("sourcesJar")
    }

    def "calling withJavadocJar before a component is available works"() {
        given:
        buildFile << """
            plugins {
                id 'java-base'
            }
            java {
                withJavadocJar()
            }
            apply plugin: 'java-library'
        """

        expect:
        succeeds("javadoc")
    }

    def "calling consistentResolution(Action) before a component is available works"() {
        given:
        buildFile << """
            plugins {
                id 'java-base'
            }

            java {
                consistentResolution {

                }
            }
            apply plugin: 'java-library'
        """

        expect:
        succeeds("help")
    }

    def "source set output classes dirs are instances of ConfigurableFileCollection"() {
        given:
        buildFile << """
            plugins {
                id("java-base")
            }

            sourceSets {
                sources
            }

            task verify {
                assert sourceSets.sources.output.classesDirs instanceof ConfigurableFileCollection
            }
        """

        expect:
        succeeds "verify"
    }
}
