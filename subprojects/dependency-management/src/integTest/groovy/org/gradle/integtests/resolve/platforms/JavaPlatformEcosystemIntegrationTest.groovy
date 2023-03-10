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

package org.gradle.integtests.resolve.platforms

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class JavaPlatformEcosystemIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/12728")
    def "the Java Platform plugin should apply the Java derivation strategy"() {
        def mod = mavenHttpRepo.module("org", "foo", "1.0").publish()

        buildFile << """
            apply plugin: 'java-platform'

            group = 'org.gradle.bugs'
            version = '1.9'

            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }

            configurations {
                runtimeClasspath {
                    canBeConsumed = false
                    assert canBeResolved
                }
            }

            dependencies {
                runtimeClasspath platform("org:foo:1.0")
            }
        """
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        mod.pom.expectGet()
        succeeds ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.gradle.bugs:test:1.9") {
                module("org:foo:1.0") {
                    variant("platform-runtime", [
                        'org.gradle.category': 'platform',
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime'
                    ])
                    noArtifacts()
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/17179")
    def "Configuration.copy() should work when platform declares project dependency constraints"() {
        setup:
        buildFile << """
            plugins {
              id 'java-platform'
            }

            dependencies {
                constraints {
                    api(project(":lib")) { because "platform alignment" }
                }
            }

            configurations.api.copy()
        """
        settingsFile << "include 'lib'"

        expect:
        succeeds ":help"
    }
}
