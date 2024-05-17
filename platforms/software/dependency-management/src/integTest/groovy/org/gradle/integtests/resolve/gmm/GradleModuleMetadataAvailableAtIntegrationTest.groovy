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

package org.gradle.integtests.resolve.gmm

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Issue

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class GradleModuleMetadataAvailableAtIntegrationTest extends AbstractModuleDependencyResolveTest {
    def "resolves available-at variant even if dependency is not transitive"() {
        given:
        repository {
            'org:moduleA:1.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../external/1.0/external-1.0.module", "org", "external", "1.0")
                }
            }
            'org:external:1.0' {
                dependsOn('org:do_not_reach:1.0')
            }
        }

        buildFile << """
            dependencies {
                conf("org:moduleA:1.0") {
                    transitive = false
                }
            }
        """

        when:
        repositoryInteractions {
            'org:moduleA:1.0' {
                expectGetMetadata()
            }
            'org:external:1.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:moduleA:1.0") {
                    noArtifacts()
                    module("org:external:1.0")
                }
            }
        }
    }

    def "resolves available-at variant even if configuration is not transitive"() {
        given:
        repository {
            'org:moduleA:1.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../external/1.0/external-1.0.module", "org", "external", "1.0")
                }
            }
            'org:external:1.0' {
                dependsOn('org:do_not_reach:1.0')
            }
        }

        buildFile << """
            dependencies {
                conf("org:moduleA:1.0")
            }
            configurations.conf.transitive = false
        """

        when:
        repositoryInteractions {
            'org:moduleA:1.0' {
                expectGetMetadata()
            }
            'org:external:1.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:moduleA:1.0") {
                    noArtifacts()
                    module("org:external:1.0")
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/14017")
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "resolves available-at variant even if transitive dependencies are excluded"() {
        given:
        mavenHttpRepo.module('org', 'moduleB', '1.0')
            .dependsOn(mavenHttpRepo.module("org", "moduleA", "1.0"), exclusions: [[group: '*', module: '*']])
            .publish() // no Gradle Module metadata for this one because we don't support excludes on dependencies
        repository {
            'org:moduleA:1.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../external/1.0/external-1.0.module", "org", "external", "1.0")
                }
            }
            'org:external:1.0' {
                dependsOn('org:do_not_reach:1.0')
            }
        }

        buildFile << """
            dependencies {
                conf("org:moduleB:1.0")
            }
        """

        when:
        repositoryInteractions {
            'org:moduleB:1.0' {
                withoutGradleMetadata()
                expectResolve()
            }
            'org:moduleA:1.0' {
                expectGetMetadata()
            }
            'org:external:1.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:moduleB:1.0") {
                    module("org:moduleA:1.0") {
                        noArtifacts()
                        module("org:external:1.0")
                    }
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/14017")
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "honors upstream excludes with available-at variant"() {
        given:
        mavenHttpRepo.module('org', 'moduleB', '1.0')
            .dependsOn(mavenHttpRepo.module("org", "moduleA", "1.0"))
            .publish() // no Gradle Module metadata for this one because we don't support excludes on dependencies
        repository {
            'org:moduleA:1.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../external/1.0/external-1.0.module", "org", "external", "1.0")
                }
            }
            'org:external:1.0' {
                dependsOn('org:do_not_reach:1.0')
            }
        }

        buildFile << """
            dependencies {
                conf("org:moduleB:1.0")
            }
            configurations {
                conf {
                    exclude module: 'external'
                }
            }
        """

        when:
        repositoryInteractions {
            'org:moduleB:1.0' {
                withoutGradleMetadata()
                expectResolve()
            }
            'org:moduleA:1.0' {
                expectGetMetadata()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:moduleB:1.0") {
                    module("org:moduleA:1.0") {
                        noArtifacts()
                    }
                }
            }
        }
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "resolution result can tell if a dependency is for an available-at variant"() {
        given:
        repository {
            'org:moduleA:1.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../external/1.0/external-1.0.module", "org", "external", "1.0")
                }
            }
            'org:external:1.0'()
        }

        buildFile << """
            dependencies {
                conf("org:moduleA:1.0")
            }

            tasks.named("checkDeps") {
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
                    boolean found = false
                    result.allComponents {
                        if (id instanceof ModuleComponentIdentifier && id.module == 'moduleA') {
                            found = true
                            assert variants.size() == 1
                            assert variants[0].owner.module == 'moduleA'
                            def externalVariant = variants[0].externalVariant
                            assert externalVariant.present
                            assert externalVariant.get().owner.module == 'external'
                        } else {
                            variants.each {
                                assert !it.externalVariant.present
                            }
                        }
                    }
                    assert found
                }
            }
        """

        when:
        repositoryInteractions {
            'org:moduleA:1.0' {
                expectGetMetadata()
            }
            'org:external:1.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:moduleA:1.0") {
                    noArtifacts()
                    module("org:external:1.0")
                }
            }
        }
    }

    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "resolution result ignores an ignored available-at variant"() {
        given:
        repository {
            'org:moduleA:1.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../external/1.0/external-1.0.module", "org", "external", "1.0")
                }
            }
            'org:external:1.0'()
        }

        buildFile << """
            dependencies {
                conf("org:moduleA:1.0@module")
            }

            tasks.named("checkDeps") {
                doLast {
                    def result = configurations.conf.incoming.resolutionResult
                    boolean found = false
                    result.allComponents {
                        if (id instanceof ModuleComponentIdentifier && id.module == 'moduleA') {
                            found = true
                            assert variants.size() == 1
                            assert variants[0].owner.module == 'moduleA'
                            def externalVariant = variants[0].externalVariant
                            assert !externalVariant.present
                        } else {
                            variants.each {
                                assert !it.externalVariant.present
                            }
                        }
                    }
                    assert found
                }
            }
        """

        when:
        repositoryInteractions {
            'org:moduleA:1.0' {
                expectGetMetadata()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:moduleA:1.0") {
                    artifact([type: 'module'])
                }
            }
        }
    }

    def "does not resolve available-at variant when using artifact notation"() {
        given:
        repository {
            'org:moduleA:1.0' {
                variants(["api", "runtime"]) {
                    availableAt("../../external/1.0/external-1.0.module", "org", "external", "1.0")
                }
            }
            'org:external:1.0' {
                dependsOn('org:do_not_reach:1.0')
            }
        }

        buildFile << """
            dependencies {
                conf("org:moduleA:1.0@module")
            }
        """

        when:
        repositoryInteractions {
            'org:moduleA:1.0' {
                expectGetMetadata()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:moduleA:1.0") {
                    artifact([type: 'module'])
                }
            }
        }
    }


}
