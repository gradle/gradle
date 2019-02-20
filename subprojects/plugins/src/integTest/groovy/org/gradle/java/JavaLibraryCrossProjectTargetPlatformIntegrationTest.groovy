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
import spock.lang.Unroll

class JavaLibraryCrossProjectTargetPlatformIntegrationTest extends AbstractIntegrationSpec {
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
            configurations.compileClasspath.attributes.attribute(TargetJavaPlatform.TARGET_PLATFORM_ATTRIBUTE, 6)
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause('''Unable to find a matching variant of project :producer:
  - Variant 'apiElements' capability test:producer:unspecified:
      - Found org.gradle.dependency.bundling 'external' but wasn't required.
      - Required org.gradle.jvm.platform '6' and found incompatible value '7'.
      - Required org.gradle.usage 'java-api' and found compatible value 'java-api-jars'.
  - Variant 'runtimeElements' capability test:producer:unspecified:
      - Found org.gradle.dependency.bundling 'external' but wasn't required.
      - Required org.gradle.jvm.platform '6' and found incompatible value '7'.
      - Required org.gradle.usage 'java-api' and found compatible value 'java-runtime-jars'.''')
    }

    @Unroll
    def "can select the most appropriate producer variant (#expected) based on target compatibility (#requested)"() {
        file('producer/build.gradle') << """
            // avoid test noise so that typically version 8 is not selected when running on JDK 8
            configurations.apiElements.attributes.attribute(TargetJavaPlatform.TARGET_PLATFORM_ATTRIBUTE, 1000)
            
            [6, 7, 9].each { v ->
                configurations {
                    "apiElementsJdk\${v}" {
                        canBeConsumed = true
                        canBeResolved = false
                        attributes {
                            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'java-api-jars'))
                            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, 'external'))
                            attribute(TargetJavaPlatform.TARGET_PLATFORM_ATTRIBUTE, v)
                        }
                    }
                }
                artifacts {
                    "apiElementsJdk\${v}" file("producer-jdk\${v}.jar")
                }
            }
        """
        buildFile << """
            configurations.compileClasspath.attributes.attribute(TargetJavaPlatform.TARGET_PLATFORM_ATTRIBUTE, $requested)
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(':producer', 'test:producer:') {
                    variant(expected, [
                            'org.gradle.dependency.bundling': 'external',
                            'org.gradle.jvm.platform': selected,
                            'org.gradle.usage':'java-api-jars'
                    ])
                    artifact(classifier: "jdk${selected}")
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

    def "can disable automatic setting of target JVM attribute"() {
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
        failure.assertHasCause("""Unable to find a matching variant of project :producer:
  - Variant 'apiElements' capability test:producer:unspecified:
      - Found org.gradle.dependency.bundling 'external' but wasn't required.
      - Required org.gradle.jvm.platform '6' and found incompatible value '7'.
      - Required org.gradle.usage 'java-api' and found compatible value 'java-api-jars'.
  - Variant 'runtimeElements' capability test:producer:unspecified:
      - Found org.gradle.dependency.bundling 'external' but wasn't required.
      - Required org.gradle.jvm.platform '6' and found incompatible value '7'.
      - Required org.gradle.usage 'java-api' and found compatible value 'java-runtime-jars'.""")

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
                            'org.gradle.dependency.bundling': 'external',
                            'org.gradle.jvm.platform': '7',
                            'org.gradle.usage':'java-api-jars'
                    ])
                    artifact group:'', module:'', version: '', type: '', name: 'main', noType: true
                }
            }
        }
    }
}
