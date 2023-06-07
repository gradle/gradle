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
import org.gradle.util.GradleVersion

class JavaBasePluginIntegrationTest extends AbstractIntegrationSpec {

    def "can define and build a source set with implementation dependencies"() {
        settingsFile << """
            include 'main', 'tests'
        """
        buildFile << """
            project(':main') {
                apply plugin: 'java'
            }
            project(':tests') {
                apply plugin: 'java-base'
                sourceSets {
                    unitTest {
                    }
                }
                dependencies {
                    unitTestImplementation project(':main')
                }
            }
        """
        file("main/src/main/java/Main.java") << """public class Main { }"""
        file("tests/src/unitTest/java/Test.java") << """public class Test { Main main = null; }"""

        expect:
        succeeds(":test:unitTestClasses")
        file("main/build/classes/java/main").assertHasDescendants("Main.class")
        file("tests/build/classes/java/unitTest").assertHasDescendants("Test.class")
    }

    def "calling withSourcesJar is deprecated when the java plugin is not applied"() {
        given:
        buildFile << """
            plugins {
                id 'java-base'
            }

            sourceSets {
                main
            }

            java {
                withSourcesJar()
            }
        """

        expect:
        executer.expectDeprecationWarning("withSourcesJar() was called without the presence of the java component. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Apply a JVM component plugin such as: java-library, application, groovy, or scala Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#java_extension_without_java_component")
        succeeds("help")
    }

    def "calling withJavadocJar is deprecated when the java plugin is not applied"() {
        given:
        buildFile << """
            plugins {
                id 'java-base'
            }

            sourceSets {
                main
            }

            task javadoc {

            }

            java {
                withJavadocJar()
            }
        """

        expect:
        executer.expectDeprecationWarning("withJavadocJar() was called without the presence of the java component. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Apply a JVM component plugin such as: java-library, application, groovy, or scala Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#java_extension_without_java_component")
        succeeds("help")
    }

    def "calling consistentResolution(Action) is deprecated when the java plugin is not applied"() {
        given:
        buildFile << """
            plugins {
                id 'java-base'
            }

            sourceSets {
                main
                test
            }

            java {
                consistentResolution {

                }
            }
        """

        expect:
        executer.expectDeprecationWarning("consistentResolution(Action) was called without the presence of the java component. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Apply a JVM component plugin such as: java-library, application, groovy, or scala Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#java_extension_without_java_component")
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
