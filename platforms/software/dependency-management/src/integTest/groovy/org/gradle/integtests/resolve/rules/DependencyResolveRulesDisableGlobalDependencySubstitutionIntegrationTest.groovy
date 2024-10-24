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

    ResolveTestFixture resolveLocal
    ResolveTestFixture resolvePublished

    def setup() {
        resolveLocal = new ResolveTestFixture(buildFile, 'localPath')
        resolveLocal.expectDefaultConfiguration('runtime')
        resolvePublished = new ResolveTestFixture(buildFile, 'publishedPath')
        resolvePublished.expectDefaultConfiguration('runtime')
        resolveLocal.addJavaEcosystem()

        mavenRepo.module("org.test", "m2", "1.0").dependsOn("org.test", "m3", "1.0").withModuleMetadata().publish()
        mavenRepo.module("org.test", "m3", '1.0').withModuleMetadata().publish()

        createDirs("m1", "m2", "m3")
        settingsFile << """
            dependencyResolutionManagement {
                repositories.maven { url = "${mavenRepo.uri}" }
            }
            includeBuild '.' // enable global substitution for this build
            include 'm1', 'm2', 'm3'
        """

        buildFile << """
            allprojects {
                group = 'org.test'
                version = '0.9'
                def conf = configurations.create('conf') {
                    canBeConsumed = false
                    canBeResolved = false
                }
                configurations.create('runtime') {
                    extendsFrom(conf)
                    assert canBeConsumed
                    canBeResolved = false
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                }
            }
            project(':m1') {
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
            }
            project(':m2') {
                dependencies {
                    conf 'org.test:m3:1.0'
                }
            }
        """
    }

    def resolveLocalPath() {
        resolveLocal.prepare()
        resolveLocal
    }

    def resolvePublishedPath() {
        resolvePublished.prepare()
        resolvePublished
    }

    def static expectResolvedToLocal(ResolveTestFixture resolve) {
        resolve.expectGraph {
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
        true
    }

    def static expectResolveToPublished(ResolveTestFixture resolve) {
        resolve.expectGraph {
            root(":m1", "org.test:m1:0.9") {
                edge("org.test:m2:1.0", "org.test:m2:1.0") {
                    edge("org.test:m3:1.0", "org.test:m3:1.0") { }
                }
            }
        }
        true
    }

    def "global dependency substitution is only disabled for the configuration that it is configured for"() {
        when:
        resolveLocalPath()
        run ':m1:checkDeps'

        then:
        expectResolvedToLocal(resolveLocal)

        when:
        resolvePublishedPath()
        run ':m1:checkDeps'

        then:
        expectResolveToPublished(resolvePublished)
    }

    def "global dependency substitution can be re-enabled"() {
        given:
        buildFile << """
            project(':m1') {
                configurations.publishedPath.resolutionStrategy.useGlobalDependencySubstitutionRules.set(true)
            }
        """

        when:
        resolvePublishedPath()
        run ':m1:checkDeps'

        then:
        expectResolvedToLocal(resolvePublished)
    }


    def "global dependency substitution can be disabled for all configurations"() {
        given:
        buildFile << """
            project(':m1') {
                configurations.all {
                    resolutionStrategy.useGlobalDependencySubstitutionRules.set(false)
                }
            }
        """

        when:
        resolveLocalPath()
        run ':m1:checkDeps'

        then:
        expectResolveToPublished(resolveLocal)

        when:
        resolvePublishedPath()
        run ':m1:checkDeps'

        then:
        expectResolveToPublished(resolvePublished)
    }

}
