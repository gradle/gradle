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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache(because = "serializes the incorrect artifact in ArtifactCollection used by resolve fixture")
    def "can select both main variant and test fixtures with project dependencies"() {
        given:
        settingsFile << "include 'lib'"

        file("lib/build.gradle") << """
            configurations {
                testFixtures {
                    canBeResolved = false
                    assert canBeConsumed
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, 'java-api'))
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, 'jar'))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().majorVersion))
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
                        requireCapability('org:lib-fixtures')
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
                    variant "apiElements", ['org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', 'org.gradle.dependency.bundling': 'external', 'org.gradle.jvm.version': JavaVersion.current().majorVersion]
                    artifact name: 'main', extension: '', type: 'java-classes-directory'
                }
                project(":lib", "test:lib:") {
                    variant "testFixtures", ['org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.dependency.bundling': 'external', 'org.gradle.jvm.version': JavaVersion.current().majorVersion]
                    artifact name: 'lib-test-fixtures'
                }
            }
        }
    }

    @ToBeFixedForConfigurationCache(because = "serializes the incorrect artifact in ArtifactCollection used by resolve fixture")
    def "prefers the variant which strictly matches the requested capabilities"() {
        given:
        settingsFile << "include 'lib'"

        file("lib/build.gradle") << """
            configurations {
                testFixtures {
                    canBeResolved = false
                    assert canBeConsumed
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, 'java-api'))
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, 'jar'))
                        attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                        attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().majorVersion))
                    }
                    outgoing.capability('test:lib:1.0')
                    outgoing.capability('test:lib-fixtures:1.0')
                }
            }

            artifacts {
                testFixtures file("lib-test-fixtures.jar")
            }
        """

        buildFile << """
            dependencies {
                implementation project(':lib')
            }
        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":lib", "test:lib:") {
                    variant "apiElements", ['org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library', 'org.gradle.dependency.bundling': 'external', 'org.gradle.jvm.version': JavaVersion.current().majorVersion]
                    artifact name: 'main', extension: '', type: 'java-classes-directory'
                }
            }
        }
    }
}
