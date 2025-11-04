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
package org.gradle.integtests.resolve.strict

import org.gradle.api.attributes.Category
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.util.GradleVersion
import spock.lang.Issue

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class EndorseStrictVersionsIntegrationTest extends AbstractModuleDependencyResolveTest {

    void "can downgrade version through platform"() {
        given:
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'bar', version: '1.0')
                constraint(group: 'org', artifact: 'foo', strictly: '1.0')
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:platform:1.0') {
                    endorseStrictVersions()
                }
                conf('org:bar')
            }
        """

        when:
        repositoryInteractions {
            'org:platform:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:platform:1.0') {
                    constraint('org:bar:1.0').byConstraint()
                    constraint('org:foo:{strictly 1.0}', 'org:foo:1.0')
                }
                edge('org:bar', 'org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.0') {
                        notRequested()
                        byConstraint()
                        byAncestor()
                    }
                }
            }
        }
    }

    void "can deal with platform upgrades"() {
        given:
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'bar', version: '1.0')
                constraint(group: 'org', artifact: 'foo', strictly: '1.0')
            }
            'org:platform:2.0'() {
                constraint(group: 'org', artifact: 'bar', version: '1.0')
                constraint(group: 'org', artifact: 'foo', version: '1.0')
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
                dependsOn 'org:platform:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:platform:1.0') {
                    endorseStrictVersions()
                }
                conf('org:bar')
            }
        """

        when:
        repositoryInteractions {
            'org:platform:1.0' {
                expectGetMetadata()
            }
            'org:platform:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:platform:1.0', 'org:platform:2.0') {
                    byConflictResolution("between versions 2.0 and 1.0")
                    constraint('org:bar:1.0')
                    constraint('org:foo:1.0', 'org:foo:2.0') {
                        notRequested()
                        byConstraint()
                        byConflictResolution("between versions 2.0 and 1.0")
                    }
                }
                edge('org:bar', 'org:bar:1.0') {
                    byConstraint()
                    module('org:foo:2.0')
                    module('org:platform:2.0')
                }
            }
        }
    }

    def "multiple endorsed strict versions that target the same module fail the build if they conflict"() {
        given:
        repository {
            'org:platform-a:1.0'() {
                constraint(group: 'org', artifact: 'foo', strictly: '1.0')
            }
            'org:platform-b:1.0'() {
                constraint(group: 'org', artifact: 'foo', strictly: '2.0')
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:platform-a:1.0') {
                    endorseStrictVersions()
                }
                conf('org:platform-b:1.0') {
                    endorseStrictVersions()
                }
                conf('org:foo')
            }
        """

        when:
        repositoryInteractions {
            'org:platform-a:1.0' {
                expectGetMetadata()
            }
            'org:platform-b:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:2.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause "Could not resolve org:foo."
        failure.assertHasCause """Component is the target of multiple version constraints with conflicting requirements:
1.0 - directly in 'org:platform-a:1.0' (runtime)
2.0 - directly in 'org:platform-b:1.0' (runtime)"""
        failure.assertHasResolution "Run with :dependencyInsight --configuration conf --dependency org:foo to get more insight on how to solve the conflict."
        failure.assertHasResolution "Debugging using the dependencyInsight report is described in more detail at: https://docs.gradle.org/${GradleVersion.current().version}/userguide/viewing_debugging_dependencies.html#sec:identifying-reason-dependency-selection."
    }

    def "a module from which strict versions are endorsed can itself be influenced by strict versions endorsed form elsewhere"() {
        given:
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'foo', strictly: '1.0')
            }
            'org:baz:1.0'() {
                dependsOn 'org:bar:1.0'
            }
            'org:bar:1.0'() {
                dependsOn 'org:foo:2.0'
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:platform:1.0') {
                    endorseStrictVersions()
                }
                conf('org:baz:1.0') {
                    endorseStrictVersions()
                }
            }
        """

        when:
        repositoryInteractions {
            'org:platform:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:baz:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps', ':dependencies'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:platform:1.0') {
                    constraint('org:foo:{strictly 1.0}', 'org:foo:1.0')
                }
                module('org:baz:1.0') {
                    module('org:bar:1.0') {
                        edge('org:foo:2.0', 'org:foo:1.0') {
                            notRequested()
                            byConstraint()
                            byAncestor()
                        }
                    }
                }
            }
        }
    }

    def "strict version endorsing can be consumed from metadata"() {
        given:
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'bar', version: '1.0')
                constraint(group: 'org', artifact: 'foo', strictly: '1.0')
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
            'org:baz:1.0' {
                dependsOn(group: 'org', artifact: 'platform', version: '1.0', endorseStrictVersions: true)
                dependsOn(group: 'org', artifact: 'bar')
            }
        }

        buildFile << """
            dependencies {
                conf('org:baz:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:platform:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:baz:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:baz:1.0') {
                    module('org:platform:1.0') {
                        constraint('org:bar:1.0').byConstraint()
                        constraint('org:foo:{strictly 1.0}', 'org:foo:1.0')
                    }
                    edge('org:bar', 'org:bar:1.0') {
                        edge('org:foo:2.0', 'org:foo:1.0') {
                            notRequested()
                            byConstraint()
                            byAncestor()
                        }
                    }
                }
            }
        }
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @Issue("https://github.com/gradle/gradle/issues/33508")
    def "removing the target of an endorsed edge from the root does not cause the root to be reselected and does not cause a broken graph"() {
        given:
        repository {
            'io.grpc:grpc-protobuf-lite:1.46.0'()
            'io.grpc:grpc-protobuf-lite:1.70.0'()
            'io.grpc:grpc-protobuf-lite:1.72.0'()
            'com.google.cloud:google-cloud-kms:2.65.0'()

            'com.google.cloud:libraries-bom:26.60.0'() {
                asPlatform()
                constraint(group: 'io.grpc', artifact: 'grpc-protobuf-lite', version: '1.70.0')
                constraint(group: 'com.google.cloud', artifact: 'google-cloud-kms', version: '2.65.0')
            }
            'com.google.cloud:google-cloud-kms:2.5.2'() {
                dependsOn(group: 'io.grpc', artifact: 'grpc-protobuf-lite', version: '1.46.0')
            }
            'io.grpc:grpc-bom:1.72.0'() {
                asPlatform()
                constraint(group: 'io.grpc', artifact: 'grpc-protobuf-lite', version: '1.72.0')
            }
            'org:resource-loader:1.0'() {
                variant('api') {
                    dependsOn('io.grpc:grpc-bom:1.72.0') {
                        attributes[Category.CATEGORY_ATTRIBUTE.name] = Category.REGULAR_PLATFORM
                        endorseStrictVersions = true
                    }
                }
            }
            'org:w-config:1.0'() {
                variant('api') {
                    dependsOn('io.grpc:grpc-bom:1.72.0') {
                        attributes[Category.CATEGORY_ATTRIBUTE.name] = Category.REGULAR_PLATFORM
                        endorseStrictVersions = true
                    }
                    dependsOn(group: 'org', artifact: 'resource-loader', version: '1.0')
                }
            }
            'org:m-config:1.0'() {
                dependsOn(group: 'org', artifact: 'w-config', version: '1.0')
            }
            'org:misc:1.0'() {
                dependsOn(group: 'org', artifact: 'w-config', version: '1.0')
                dependsOn(group: 'org', artifact: 'm-config', version: '1.0')
            }
            'org:m-testing:1.0'() {
                dependsOn(group: 'org', artifact: 'misc', version: '1.0')
            }
            'org.junit:junit-bom:5.12.0'() {
                asPlatform()
                constraint(group: 'org.junit.jupiter', artifact: 'junit-jupiter-api', version: '5.12.0')
            }
            'org.junit:junit-bom:5.12.2'() {
                asPlatform()
                constraint(group: 'org.junit.jupiter', artifact: 'junit-jupiter-api', version: '5.12.2')
            }
            'org.junit.jupiter:junit-jupiter-api:5.12.2'() {
                variant('api') {
                    dependsOn('org.junit:junit-bom:5.12.2') {
                        attributes[Category.CATEGORY_ATTRIBUTE.name] = Category.REGULAR_PLATFORM
                        endorseStrictVersions = true
                    }
                }
            }
        }

        buildFile << """
            apply plugin: 'java-library'
            dependencies {
                implementation(platform("com.google.cloud:libraries-bom:26.60.0")) {
                    (it as ModuleDependency).doNotEndorseStrictVersions()
                }
                implementation("com.google.cloud:google-cloud-kms:2.5.2")
                implementation("org:misc:1.0")
                implementation("org:m-testing:1.0")
                implementation(platform("org.junit:junit-bom:5.12.0"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.12.2")
            }
        """

        when:
        repositoryInteractions {
            'io.grpc:grpc-protobuf-lite:1.46.0'() {
                allowAll()
            }
            'io.grpc:grpc-protobuf-lite:1.70.0'() {
                allowAll()
            }
            'io.grpc:grpc-protobuf-lite:1.72.0'() {
                allowAll()
            }
            'com.google.cloud:google-cloud-kms:2.65.0'() {
                allowAll()
            }

            'com.google.cloud:libraries-bom:26.60.0'() {
                allowAll()
            }
            'com.google.cloud:google-cloud-kms:2.5.2'() {
                allowAll()
            }
            'io.grpc:grpc-bom:1.72.0'() {
                allowAll()
            }
            'org:resource-loader:1.0'() {
                allowAll()
            }
            'org:w-config:1.0'() {
                allowAll()
            }
            'org:m-config:1.0'() {
                allowAll()
            }
            'org:misc:1.0'() {
                allowAll()
            }
            'org:m-testing:1.0'() {
                allowAll()
            }
            'org.junit:junit-bom:5.12.0'() {
                allowAll()
            }
            'org.junit:junit-bom:5.12.2'() {
                allowAll()
            }
            'org.junit.jupiter:junit-jupiter-api:5.12.2'() {
                allowAll()
            }
        }

        then:
        succeeds(":dependencies", "--configuration", "compileClasspath")
    }

}
