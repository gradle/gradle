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

            // All this might be better to be removed in favor of just expecting the (forthcoming) deprecation warning
            class FeatureOneIsCompatible implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    if (details.getConsumerValue().startsWith('one')) {
                        details.compatible()
                    }
                }
            }
            def feature = Attribute.of('org.gradle.feature', String)

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
                attributesSchema {
                    attribute(feature) {
                        compatibilityRules.add(FeatureOneIsCompatible)
                    }
                }
            }
        """
        file("p1/build.gradle") << """
            apply plugin: 'java'

            class FeatureOneIsCompatible implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    if (details.getConsumerValue().startsWith('one')) {
                        details.compatible()
                    }
                }
            }
            def feature = Attribute.of('org.gradle.feature', String)

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
                attributesSchema {
                    attribute(feature) {
                        compatibilityRules.add(FeatureOneIsCompatible)
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

            class FeatureOneIsCompatible implements AttributeCompatibilityRule<String> {
                void execute(CompatibilityCheckDetails<String> details) {
                    if (details.getConsumerValue().startsWith('one')) {
                        details.compatible()
                    }
                }
            }
            def feature = Attribute.of('org.gradle.feature', String)

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
                attributesSchema {
                    attribute(feature) {
                        compatibilityRules.add(FeatureOneIsCompatible)
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
                        'org.gradle.feature': 'onePreferred',
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
                        'org.gradle.feature': 'twoPreferred',
                        'org.gradle.jvm.version': "${JavaVersion.current().majorVersion}",
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.usage': 'java-runtime'])
                }
            }
        }
    }
}
