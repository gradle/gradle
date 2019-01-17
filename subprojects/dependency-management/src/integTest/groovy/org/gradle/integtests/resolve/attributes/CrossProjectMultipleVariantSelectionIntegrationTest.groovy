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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class CrossProjectMultipleVariantSelectionIntegrationTest extends AbstractDependencyResolutionTest {

    ResolveTestFixture resolve

    def setup() {
        buildFile << """
            allprojects {
                apply plugin: 'java-library'
            }
        """
        settingsFile << """
            rootProject.name = 'test'
        """
        resolve = new ResolveTestFixture(buildFile, "compileClasspath")
        resolve.prepare()
    }

    def "can select both main variant and test fixtures with project dependencies"() {
        given:
        settingsFile << "include 'lib'"

        file("lib/build.gradle") << """
            configurations {
                testFixtures {
                    canBeResolved = false
                    canBeConsumed = true
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, 'java-api'))
                    }
                    outgoing.capability('org:lib-fixtures:1.0')
                }
            }

            artifacts {
                testFixtures file("lib-test-fixtures.jar")
            }
        """

        buildFile << """
            dependencies {
                implementation project(':lib')
                implementation (project(':lib')) {
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, 'java-api'))
                    }
                    capabilities {
                        requireCapability('org:lib-fixtures:1.0')
                    }
                }
            }
        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":lib", "test:lib:") {
                    variant "apiElements", ['org.gradle.usage':'java-api']
                    artifact group:'', module:'', version: '', type: '', name: 'main', noType: true
                }
                project(":lib", "test:lib:") {
                    variant "testFixtures", ['org.gradle.usage':'java-api']
                    artifact group:'test', module:'lib', version:'unspecified', classifier: 'test-fixtures'
                }
            }
        }
    }

}
