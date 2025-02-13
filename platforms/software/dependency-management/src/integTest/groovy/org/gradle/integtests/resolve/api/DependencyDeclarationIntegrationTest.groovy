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
    @Issue("https://github.com/gradle/gradle/issues/32437")
    def 'all properties of dependencies are copied when the dependency is copied'() {
        disableProblemsApiCheck()

        given:
        def baseProperties = """
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

        def targetConfigurationProperties = """
            transitive = false
            targetConfiguration = "abc"
            doNotEndorseStrictVersions()
        """

        def externalBaseProperties = """
            $baseProperties

            version {
                branch = "branch"
                strictly("123")
                prefer("789")
                reject("aaa")
            }

            changing = true
        """

        def externalTargetConfigurationProperties = """
            $targetConfigurationProperties

            version {
                require("456")
            }

            changing = false
        """

        def variantAwareProperties = """
            attributes {
                attribute(Attribute.of('foo', String), 'bar')
            }
            capabilities {
                requireCapability("org:test-cap:1.1")
            }
        """

        file("gradle/libs.versions.toml") << """[libraries]
test1 = { module = 'org:test1', version = '1.0' }
test2 = { module = 'org:test2', version = '1.0' }
test3 = { module = 'org:test3', version = '1.0' }
"""

        settingsFile << """
            include("test7")
            include("test8")
            include("test9")
        """
        createDirs("test7", "test8", "test9")

        buildFile << """
            configurations {
                implementation
                destination1
                destination2
            }

            dependencies {
                implementation(libs.test1) {
                    ${externalBaseProperties}
                }
                implementation(libs.test2) {
                    ${externalTargetConfigurationProperties}
                }
                implementation(libs.test3) {
                    ${variantAwareProperties}
                }
                implementation("org:test4") {
                    ${externalBaseProperties}
                }
                implementation("org:test5") {
                    ${externalTargetConfigurationProperties}
                }
                implementation("org:test6") {
                    ${variantAwareProperties}
                }
                implementation(project(":test7")) {
                    ${baseProperties}
                }
                implementation(project(":test8")) {
                    ${targetConfigurationProperties}
                }
                implementation(project(":test9")) {
                    ${variantAwareProperties}
                }
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
        """

        expect:
        succeeds "help"
    }

}
