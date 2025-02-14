/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Tests user-interactions with the {@code dependencies} block, and
 * {@link org.gradle.api.artifacts.Dependency} types.
 */
class DependencyDeclarationIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/23096")
    def "base properties are copied for version catalog dependency"() {
        given:
        file("gradle/libs.versions.toml") << """[libraries]
test = { module = 'org:test', version = '1.0' }
"""

        buildFile << """
            $copyTestBase

            dependencies {
                implementation(libs.test) {
                    ${baseProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    @Issue("https://github.com/gradle/gradle/issues/23096")
    def "targetConfiguration properties are copied for version catalog dependency"() {
        file("gradle/libs.versions.toml") << """[libraries]
test = { module = 'org:test', version = '1.0' }
"""

        buildFile << """
            $copyTestBase

            dependencies {
                implementation(libs.test) {
                    ${externalTargetConfigurationProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    @Issue("https://github.com/gradle/gradle/issues/23096")
    def "variant aware properties are copied for version catalog dependency"() {
        file("gradle/libs.versions.toml") << """[libraries]
test = { module = 'org:test', version = '1.0' }
"""

        buildFile << """
            $copyTestBase

            dependencies {
                implementation(libs.test) {
                    ${variantAwareProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    def "base properties are copied for external dependency"() {
        buildFile << """
            $copyTestBase

            dependencies {
                implementation("org:test") {
                    ${externalBaseProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    def "targetConfiguration properties are copied for external dependency"() {
        buildFile << """
            $copyTestBase

            dependencies {
                implementation("org:test") {
                    ${externalTargetConfigurationProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    def "variant aware properties are copied for external dependency"() {
        buildFile << """
            $copyTestBase

            dependencies {
                implementation("org:test") {
                    ${variantAwareProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    @Issue("https://github.com/gradle/gradle/issues/32437")
    def "base properties are copied for project dependency"() {
        settingsFile << "include('test')"
        createDirs("test")

        buildFile << """
            $copyTestBase

            dependencies {
                implementation(project(":test")) {
                    ${baseProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    @Issue("https://github.com/gradle/gradle/issues/32437")
    def "targetConfiguration properties are copied for project dependency"() {
        settingsFile << "include('test')"
        createDirs("test")

        buildFile << """
            $copyTestBase

            dependencies {
                implementation(project(":test")) {
                    ${targetConfigurationProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    @Issue("https://github.com/gradle/gradle/issues/32437")
    def "variant aware properties are copied for project dependency"() {
        settingsFile << "include('test')"
        createDirs("test")

        buildFile << """
            $copyTestBase

            dependencies {
                implementation(project(":test")) {
                    ${variantAwareProperties}
                }
            }
        """

        expect:
        succeeds("verifyCopy")
    }

    String getCopyTestBase() {
        """
            configurations {
                implementation
                destination1
                destination2
            }

            def verifyDep(Dependency original, Dependency copied) {
                // Dependency
                assert original.group == copied.group
                assert original.name == copied.name
                assert original.version == copied.version
                assert original.reason == copied.reason

                if (original instanceof ModuleDependency) {
                    assert copied instanceof ModuleDependency

                    assert original.excludeRules == copied.excludeRules
                    assert original.artifacts == copied.artifacts
                    assert original.transitive == copied.transitive
                    assert original.targetConfiguration == copied.targetConfiguration
                    assert original.attributes == copied.attributes
                    assert original.capabilitySelectors == copied.capabilitySelectors
                    assert original.endorsingStrictVersions == copied.endorsingStrictVersions
                }

                if (original instanceof ExternalDependency) {
                    assert copied instanceof ExternalDependency

                    assert original.versionConstraint == copied.versionConstraint
                }

                if (original instanceof ExternalModuleDependency) {
                    assert copied instanceof ExternalModuleDependency

                    assert original.changing == copied.changing
                }

                if (original instanceof ProjectDependency) {
                    assert copied instanceof ProjectDependency

                    assert original.path == copied.path
                }
            }

            def getOriginal(dep) {
                configurations.implementation.dependencies.find { it.name == dep.name }
            }

            tasks.register("verifyCopy") {
                configurations.implementation.dependencies.each {
                    project.dependencies.add("destination1", it)
                    configurations.destination2.dependencies.add(it)
                }

                configurations.destination1.dependencies.each {
                    verifyDep(getOriginal(it), it)
                }

                configurations.destination2.dependencies.each {
                    verifyDep(getOriginal(it), it)
                }

                configurations.implementation.dependencies.each {
                    verifyDep(it, it.copy())
                }

                configurations.implementation.copy().dependencies.each {
                    verifyDep(getOriginal(it), it)
                }

                configurations.detachedConfiguration(configurations.implementation.dependencies.toArray(new Dependency[0])).dependencies.each {
                    verifyDep(getOriginal(it), it)
                }
            }
        """
    }

    private static String getBaseProperties() {
        """
            because("reason1")

            exclude(group: "test-group", module: "test-module")
            artifact {
                name = "test-name"
                classifier = "test-classifier"
                extension = "test-ext"
                type = "test-type"
                url = "test-url"
            }
            transitive = true
            endorseStrictVersions()
        """
    }

    private static String getTargetConfigurationProperties() {
        """
            transitive = false
            targetConfiguration = "abc"
            doNotEndorseStrictVersions()
        """
    }

    private static String getVariantAwareProperties() {
        """
            attributes {
                attribute(Attribute.of('foo', String), 'bar')
            }
            capabilities {
                requireCapability("org:test-cap:1.1")
            }
        """
    }

    private static String getExternalBaseProperties() {
        """
            $baseProperties

            version {
                branch = "branch"
                strictly("123")
                prefer("789")
                reject("aaa")
            }

            changing = true
        """
    }

    private static String getExternalTargetConfigurationProperties() {
        """
            $targetConfigurationProperties

            version {
                require("456")
            }

            changing = false
        """
    }

}
