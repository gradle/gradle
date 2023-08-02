/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.hamcrest.Matchers

class JavaLibraryCrossProjectTargetJvmVersionIntegrationTest extends AbstractIntegrationSpec {
    ResolveTestFixture resolve

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
            include 'producer'
        """
        buildFile << """
            allprojects {
                apply plugin: 'java-library'
            }

            dependencies {
                api project(':producer')
            }
        """
        resolve = new ResolveTestFixture(buildFile, 'compileClasspath')
        resolve.prepare()
    }

    def "can fail resolution if producer doesn't have appropriate target version"() {
        file('producer/build.gradle') << """
            java {
                sourceCompatibility = JavaVersion.VERSION_1_7
                targetCompatibility = JavaVersion.VERSION_1_7
            }
        """
        buildFile << """
            configurations.compileClasspath.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 6)
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause('''No matching variant of project :producer was found. The consumer was configured to find a library for use during compile-time, compatible with Java 6, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
  - Variant 'apiElements' capability test:producer:unspecified declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 7 and the consumer needed a component, compatible with Java 6
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
  - Variant 'mainSourceElements' capability test:producer:unspecified declares a component, and its dependencies declared externally:
      - Incompatible because this component declares a component of category 'verification' and the consumer needed a library
      - Other compatible attributes:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
          - Doesn't say anything about its target Java version (required compatibility with Java 6)
          - Doesn't say anything about its elements (required them preferably in the form of class files)
          - Doesn't say anything about its usage (required compile-time)
  - Variant 'runtimeElements' capability test:producer:unspecified declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 7 and the consumer needed a component, compatible with Java 6
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
  - Variant 'testResultsElementsForTest' capability test:producer:unspecified:
      - Incompatible because this component declares a component of category 'verification' and the consumer needed a library
      - Other compatible attributes:
          - Doesn't say anything about how its dependencies are found (required its dependencies declared externally)
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
          - Doesn't say anything about its target Java version (required compatibility with Java 6)
          - Doesn't say anything about its elements (required them preferably in the form of class files)
          - Doesn't say anything about its usage (required compile-time)''')
    }

    def "can select the most appropriate producer variant (#expected) based on target compatibility (#requested)"() {
        file('producer/build.gradle') << """
            // avoid test noise so that typically version 8 is not selected when running on JDK 8
            configurations.apiElements.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 1000)
            configurations.runtimeElements.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 1000)

            [6, 7, 9].each { v ->
                configurations {
                    "apiElementsJdk\${v}" {
                        assert canBeConsumed
                        canBeResolved = false
                        attributes {
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'java-api'))
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, 'jar'))
                            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, 'external'))
                            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, v)
                        }
                    }
                }
                artifacts {
                    "apiElementsJdk\${v}" file("producer-jdk\${v}.jar")
                }
            }
        """
        buildFile << """
            configurations.compileClasspath.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, $requested)
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(':producer', 'test:producer:') {
                    variant(expected, [
                            'org.gradle.dependency.bundling': 'external',
                            'org.gradle.jvm.version': selected,
                            'org.gradle.usage':'java-api',
                            'org.gradle.libraryelements': 'jar'
                    ])
                    artifact(name: "producer-jdk${selected}")
                }
            }
        }

        where:
        requested | selected
        6         | 6
        7         | 7
        8         | 7
        9         | 9
        10        | 9

        expected = "apiElementsJdk$selected"
    }

    def "can disable selection of dependencies based on jvm version"() {
        file("producer/build.gradle") << """
            java {
                targetCompatibility = JavaVersion.VERSION_1_7
            }
        """
        buildFile << """
            java {
                targetCompatibility = JavaVersion.VERSION_1_6
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertThatCause(Matchers.startsWith("No matching variant of project :producer was found."))

        when:
        buildFile << """
            java {
                disableAutoTargetJvm()
            }
        """
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(':producer', 'test:producer:') {
                    variant("apiElements", [
                            'org.gradle.category': 'library',
                            'org.gradle.dependency.bundling': 'external',
                            'org.gradle.jvm.version': 7,
                            'org.gradle.usage':'java-api',
                            'org.gradle.libraryelements': 'jar'
                    ])
                    artifact name: 'main', extension: '', type: 'java-classes-directory'
                }
            }
        }
    }
}
