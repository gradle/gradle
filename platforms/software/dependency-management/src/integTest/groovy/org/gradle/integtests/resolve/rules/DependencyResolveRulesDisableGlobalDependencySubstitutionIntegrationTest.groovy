/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class DependencyResolveRulesDisableGlobalDependencySubstitutionIntegrationTest extends AbstractIntegrationSpec {

    ResolveTestFixture resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        mavenRepo.module("org.test", "m2", "1.0").dependsOn("org.test", "m3", "1.0").withModuleMetadata().publish()
        mavenRepo.module("org.test", "m3", '1.0').withModuleMetadata().publish()

        settingsFile << """
            dependencyResolutionManagement {
                repositories.maven { url = "${mavenRepo.uri}" }
            }
            includeBuild '.' // enable global substitution for this build
            include 'm1', 'm2', 'm3'
        """

        def common = """
            group = 'org.test'
            version = '0.9'
            configurations {
                conf {
                    canBeConsumed = false
                    canBeResolved = false
                }
                runtime {
                    extendsFrom(conf)
                    assert canBeConsumed
                    canBeResolved = false
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                }
            }
        """

        file("m1/build.gradle") << """
            plugins {
                id("jvm-ecosystem")
            }

            $common

            ${resolve.configureProject("localPath", "publishedPath")}

            configurations.create('localPath') {
                extendsFrom(configurations.conf)
                canBeConsumed = false
                assert canBeResolved
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            }
            configurations.create('publishedPath') {
                extendsFrom(configurations.conf)
                canBeConsumed = false
                assert canBeResolved
                resolutionStrategy.useGlobalDependencySubstitutionRules.set(false)
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
            }
            dependencies {
                conf 'org.test:m2:1.0'
            }
        """

        file("m2/build.gradle") << """
            $common

            dependencies {
                conf 'org.test:m3:1.0'
            }
        """

        file("m3/build.gradle") << common
    }

    void expectResolvedToLocal() {
        resolve.expectGraph(":m1") {
            root(":m1", "org.test:m1:0.9") {
                edge("org.test:m2:1.0", ":m2", "org.test:m2:0.9") {
                    compositeSubstitute()
                    noArtifacts()
                    edge("org.test:m3:1.0", ":m3", "org.test:m3:0.9") {
                        compositeSubstitute()
                        noArtifacts()
                    }
                }
            }
        }
    }

    void expectResolveToPublished() {
        resolve.expectGraph(":m1") {
            root(":m1", "org.test:m1:0.9") {
                edge("org.test:m2:1.0", "org.test:m2:1.0") {
                    edge("org.test:m3:1.0", "org.test:m3:1.0") { }
                }
            }
        }
    }

    def "global dependency substitution is only disabled for the configuration that it is configured for"() {
        when:
        run ':m1:checkLocalPath'

        then:
        expectResolvedToLocal()

        when:
        run ':m1:checkPublishedPath'

        then:
        expectResolveToPublished()
    }

    def "global dependency substitution can be re-enabled"() {
        given:
        file("m1/build.gradle") << """
            configurations.publishedPath.resolutionStrategy.useGlobalDependencySubstitutionRules.set(true)
        """

        when:
        run ':m1:checkPublishedPath'

        then:
        expectResolvedToLocal()
    }


    def "global dependency substitution can be disabled for all configurations"() {
        given:
        file("m1/build.gradle") << """
            configurations.all {
                resolutionStrategy.useGlobalDependencySubstitutionRules.set(false)
            }
        """

        when:
        run ':m1:checkLocalPath'

        then:
        expectResolveToPublished()

        when:
        run ':m1:checkPublishedPath'

        then:
        expectResolveToPublished()
    }

}
