/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r75

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseClasspathEntry
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseProjectDependency

// Test-dependency classification branches that only produce the "test" classpath attribute from Gradle 7.5 onwards.
@TargetGradleVersion(">=7.5")
class ToolingApiEclipseTestDependencyClassificationCrossVersionSpec extends ToolingApiSpecification {

    private static boolean hasTestAttribute(EclipseClasspathEntry entry) {
        entry.classpathAttributes.find { it.name == 'test' && it.value == 'true' }
    }

    def "external dependency in a jvm test suite has the test attribute"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
                id 'jvm-test-suite'
                id 'eclipse'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    integration(JvmTestSuite) {
                        dependencies {
                            implementation 'com.google.guava:guava:21.0'
                        }
                    }
                }
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def guava = project.classpath.find { it.file.name.contains 'guava' }

        then:
        hasTestAttribute(guava)
    }

    def "project dependency in a jvm test suite has the test attribute"() {
        setup:
        includeProjects("a", "b")
        file('a/build.gradle') << """
            plugins {
                id 'java-library'
                id 'jvm-test-suite'
                id 'eclipse'
            }

            testing {
                suites {
                    integration(JvmTestSuite) {
                        dependencies {
                            implementation project(':b')
                        }
                    }
                }
            }
        """
        file('b/build.gradle') << "plugins { id 'java-library' }"

        when:
        EclipseProject root = loadToolingModel(EclipseProject)
        EclipseProject projectA = root.children.find { it.gradleProject.path == ':a' }
        EclipseProjectDependency depB = projectA.projectDependencies.find { it.path == 'b' }

        then:
        hasTestAttribute(depB)
    }

    def "test configurations are configurable for project dependencies"() {
        setup:
        includeProjects("a", "b", "c")
        file('a/build.gradle') << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }

            configurations {
                integration
            }

            dependencies {
                integration project(':b')
                testImplementation project(':c')
            }

            eclipse {
                classpath {
                    plusConfigurations += [configurations.integration]
                    testConfigurations = [configurations.integration]
                }
            }
        """
        file('b/build.gradle') << "plugins { id 'java-library' }"
        file('c/build.gradle') << "plugins { id 'java-library' }"

        when:
        EclipseProject root = loadToolingModel(EclipseProject)
        EclipseProject projectA = root.children.find { it.gradleProject.path == ':a' }
        EclipseProjectDependency depB = projectA.projectDependencies.find { it.path == 'b' }
        EclipseProjectDependency depC = projectA.projectDependencies.find { it.path == 'c' }

        then:
        hasTestAttribute(depB)
        !hasTestAttribute(depC)
    }

    def "project dependency in a custom source set has the test attribute only when the source set name contains 'test'"() {
        setup:
        includeProjects("a", "b", "c")
        file('a/build.gradle') << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }

            sourceSets {
                functionalTest
                integration
            }

            dependencies {
                functionalTestImplementation project(':b')
                integrationImplementation project(':c')
            }
        """
        file('b/build.gradle') << "plugins { id 'java-library' }"
        file('c/build.gradle') << "plugins { id 'java-library' }"

        when:
        EclipseProject root = loadToolingModel(EclipseProject)
        EclipseProject projectA = root.children.find { it.gradleProject.path == ':a' }
        EclipseProjectDependency depB = projectA.projectDependencies.find { it.path == 'b' }
        EclipseProjectDependency depC = projectA.projectDependencies.find { it.path == 'c' }

        then:
        hasTestAttribute(depB)
        !hasTestAttribute(depC)
    }
}
