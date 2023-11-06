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

import groovy.test.NotYetImplemented
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class CapabilitiesConflictResolutionIssuesIntegrationTest extends AbstractIntegrationSpec {

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
            rootProject.name = 'test'
            include 'shared'
            include 'p1'
            include 'p2'
        """
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        run ":p1:checkDeps"

        then:
        resolve.expectGraph {
            root(":p1", "test:p1:") {
                project(":p2", "test:p2:") {
                    configuration 'runtimeElements'
//                    project(":shared", "test:shared:") {
//                        artifact(classifier: 'one-preferred')
//                    }
//                    project(":shared", "test:shared:") {
//                        artifact(classifier: 'two-preferred')
//                    }
                }
                project(":shared", "test:shared:") {
                    variant('onePrefRuntimeElements', [
                        'org.gradle.category': 'library',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.jvm.version': "${JavaVersion.current().majorVersion}",
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.usage': 'java-runtime'])
                    byConflictResolution()
                    artifact(classifier: 'one-preferred')
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
                    artifact(classifier: 'two-preferred')
                }
            }
        }
    }

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/26145")
    def "dependencies can be resolved with multiple capability replacements"() {
        buildFile << """
            plugins {
                id 'java'
                id 'dev.jacomet.logging-capabilities' version '0.11.1'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation 'eu.medsea.mimeutil:mime-util:2.1.3'
                implementation 'org.slf4j:slf4j-api:2.0.7'
                runtimeOnly 'ch.qos.logback:logback-classic:1.3.11'
            }

            loggingCapabilities {
                enforceLogback()
            }
        """

        expect:
        succeeds("dependencies", "--configuration=runtimeClasspath")
    }

    def "can evict node in a component with an un-evicted selected node"() {
        settingsFile << "include 'producer'"
        file("producer/build.gradle") << """
            configurations {
                consumable("one") {
                    outgoing {
                        capability('o:n:e')
                    }
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, "foo"))
                    }
                }
                consumable("one-preferred") {
                    outgoing {
                        capability('o:n:e')
                        capability('g:one-preferred:v')
                    }
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, "foo"))
                    }
                }
            }
        """
        buildFile << """
            configurations {
                dependencyScope("implementation")
                resolvable("classpath") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, "foo"))
                    }
                }
            }

            configurations.classpath {
                resolutionStrategy.capabilitiesResolution.all { details ->
                    def selection =
                        details.candidates.find { it.variantName.endsWith('preferred') }
                    println("Selecting \$selection from \${details.candidates}")
                    details.select(selection)
                }
            }

            dependencies {
                implementation(project(':producer')) {
                    capabilities {
                        requireCapability('o:n:e')
                    }
                }
                implementation(project(':producer')) {
                    capabilities {
                        requireCapability('o:n:e')
                        requireCapability('g:one-preferred:v')
                    }
                }
            }

            task resolve {
                def root = configurations.classpath.incoming.resolutionResult.rootComponent
                doLast {
                    println(root.get().dependencies)
                }
            }
        """

        expect:
        succeeds(":resolve")
    }
}
