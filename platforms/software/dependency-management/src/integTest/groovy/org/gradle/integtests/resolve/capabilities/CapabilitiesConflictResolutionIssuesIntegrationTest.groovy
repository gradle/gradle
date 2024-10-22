/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.capabilities

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class CapabilitiesConflictResolutionIssuesIntegrationTest extends AbstractIntegrationSpec {

    def resolve = new ResolveTestFixture(buildFile, "runtimeClasspath")

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/14770")
    def "capabilities resolution shouldn't put graph in inconsistent state"() {
        file("shared/build.gradle") << """
            plugins {
                id 'java'
            }

            sourceSets {
                one {}
                onePref {}
                two {}
                twoPref {}
            }

            java {
                registerFeature('one') {
                    usingSourceSet(sourceSets.one)
                    capability('o', 'n', 'e')
                    capability('g', 'one', 'v')
                }
                registerFeature('onePreferred') {
                    usingSourceSet(sourceSets.onePref)
                    capability('o', 'n', 'e')
                    capability('g', 'one-preferred', 'v')
                }

                registerFeature('two') {
                    usingSourceSet(sourceSets.two)
                    capability('t', 'w', 'o')
                    capability('g', 'two', 'v')
                }
                registerFeature('twoPreferred') {
                    usingSourceSet(sourceSets.twoPref)
                    capability('t', 'w', 'o')
                    capability('g', 'two-preferred', 'v')
                }
            }

            dependencies {
                twoImplementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:one:v')
                    }
                }
                twoPrefImplementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:one-preferred:v')
                    }
                }
            }
        """
        file("p1/build.gradle") << """
            apply plugin: 'java'

            dependencies {
                implementation project(':p2')
                implementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:one-preferred:v')
                    }
                }
                implementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:two-preferred:v')
                    }
                }
            }

            configurations.compileClasspath {
                resolutionStrategy.capabilitiesResolution.all { details ->
                    def selection =
                        details.candidates.find { it.variantName.endsWith('PrefApiElements') }
                    println("Selecting \$selection from \${details.candidates}")
                    details.select(selection)
                }
            }

            configurations.runtimeClasspath {
                resolutionStrategy.capabilitiesResolution.all { details ->
                    def selection =
                        details.candidates.find { it.variantName.endsWith('PrefRuntimeElements') }
                    println("Selecting \$selection from \${details.candidates}")
                    details.select(selection)
                }
            }
        """
        file("p2/build.gradle") << """
            apply plugin: 'java'

            dependencies {
                implementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:one:v')
                    }
                }
                implementation(project(':shared')) {
                    capabilities {
                        requireCapability('g:two:v')
                    }
                }
            }
        """
        settingsFile << """
            include 'shared'
            include 'p1'
            include 'p2'
        """
        resolve.prepare()

        when:
        run ":p1:checkDeps"

        then:
        resolve.expectGraph {
            root(":p1", "test:p1:") {
                project(":p2", "test:p2:") {
                    configuration 'runtimeElements'
                    project(":shared", "test:shared:") {
                        artifact(classifier: 'one-preferred')
                    }
                    project(":shared", "test:shared:") {
                        artifact(classifier: 'two-preferred')
                    }
                }
                project(":shared", "test:shared:") {
                    variant('onePrefRuntimeElements', [
                        'org.gradle.category': 'library',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.jvm.version': "${JavaVersion.current().majorVersion}",
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.usage': 'java-runtime'])
                    byConflictResolution()
                    project(":shared", "test:shared:") {

                    }
                }
                project(":shared", "test:shared:") {
                    variant('twoPrefRuntimeElements', [
                        'org.gradle.category': 'library',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.jvm.version': "${JavaVersion.current().majorVersion}",
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.usage': 'java-runtime'])
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/30969")
    def "dependency may have same capability as its transitive dependency and fails with rejection without capability resolution rule"() {
        mavenRepo.module("org.hamcrest", "hamcrest-core", "2.2")
            .dependsOn(mavenRepo.module("org.hamcrest", "hamcrest", "2.2").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.hamcrest:hamcrest-core:2.2")
            }

            dependencies.components.withModule('org.hamcrest:hamcrest-core') {
                allVariants {
                    withCapabilities {
                        addCapability('org.hamcrest', 'hamcrest', id.version)
                    }
                }
            }
        """

        when:
        resolve.prepare()
        fails(":checkDeps")

        then:
        failure.assertHasCause("Could not resolve org.hamcrest:hamcrest-core:2.2")
        failure.assertHasCause("Module 'org.hamcrest:hamcrest-core' has been rejected")
        failure.assertHasErrorOutput("Cannot select module with conflict on capability 'org.hamcrest:hamcrest:2.2' also provided by [org.hamcrest:hamcrest:2.2(runtime)]")
        failure.assertHasCause("Module 'org.hamcrest:hamcrest' has been rejected")
        failure.assertHasErrorOutput("Cannot select module with conflict on capability 'org.hamcrest:hamcrest:2.2' also provided by [org.hamcrest:hamcrest-core:2.2(runtime)]")
    }

    @Issue("https://github.com/gradle/gradle/issues/30969")
    def "dependency may have same capability as its transitive dependency"() {
        mavenRepo.module("org.hamcrest", "hamcrest-core", "2.2")
            .dependsOn(mavenRepo.module("org.hamcrest", "hamcrest", "2.2").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.hamcrest:hamcrest-core:2.2")
            }

            dependencies.components.withModule('org.hamcrest:hamcrest-core') {
                allVariants {
                    withCapabilities {
                        addCapability('org.hamcrest', 'hamcrest', id.version)
                    }
                }
            }

            configurations.runtimeClasspath {
                resolutionStrategy {
                    capabilitiesResolution {
                        withCapability("org.hamcrest:hamcrest") {
                            def result = candidates.find {
                                it.id.group == "org.hamcrest" && it.id.module == "${winner}"
                            }
                            assert result != null
                            select(result)
                        }
                    }
                }
            }
        """

        when:
        resolve.prepare()
        succeeds(":checkDeps")

        then:
        if (winner == "hamcrest-core") {
            resolve.expectGraph {
                root(":", ":test:") {
                    module("org.hamcrest:hamcrest-core:2.2") {
                        edge("org.hamcrest:hamcrest:2.2", "org.hamcrest:hamcrest-core:2.2")
                        byConflictResolution()
                    }
                }
            }
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org.hamcrest:hamcrest-core:2.2", "org.hamcrest:hamcrest:2.2") {
                        notRequested()
                        byConflictResolution()
                    }
                }
            }
        }

        where:
        winner << ["hamcrest-core", "hamcrest"]
    }

    @Issue("https://github.com/gradle/gradle/issues/30969")
    def "dependency may have same capability as its distant transitive dependency"() {
        mavenRepo.module("org", "parent", "2.2").dependsOn(
            mavenRepo.module("org", "middle", "2.2").dependsOn(
                mavenRepo.module("org", "child", "2.2").publish()
            ).publish()
        ).publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org:parent:2.2")
            }

            dependencies.components.withModule('org:parent') {
                allVariants {
                    withCapabilities {
                        addCapability('org', 'child', id.version)
                    }
                }
            }

            configurations.runtimeClasspath {
                resolutionStrategy {
                    capabilitiesResolution {
                        withCapability("org:child") {
                            def result = candidates.find {
                                it.id.group == "org" && it.id.module == "${winner}"
                            }
                            assert result != null
                            select(result)
                        }
                    }
                }
            }
        """

        when:
        resolve.prepare()
        succeeds(":checkDeps")

        then:
        if (winner == "parent") {
            resolve.expectGraph {
                root(":", ":test:") {
                    module("org:parent:2.2") {
                        module("org:middle:2.2") {
                            edge("org:child:2.2", "org:parent:2.2") {
                                byConflictResolution()
                            }
                        }
                    }
                }
            }
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:parent:2.2", "org:child:2.2") {
                        notRequested()
                        byConflictResolution()
                    }
                }
            }
        }

        where:
        winner << ["parent", "child"]
    }
}
