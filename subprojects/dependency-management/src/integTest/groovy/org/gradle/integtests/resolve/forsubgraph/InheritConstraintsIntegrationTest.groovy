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
package org.gradle.integtests.resolve.forsubgraph

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeatures([
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")]
)
class InheritConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        resolve.withStrictReasonsCheck()
    }

    void "can downgrade version through platform"() {
        given:
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'bar', version: '1.0')
                constraint(group: 'org', artifact: 'foo', version: '1.0', forSubgraph: true)
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
                    inheritConstraints()
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
                    constraint('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byConstraint()
                }
                edge('org:bar', 'org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.0').byAncestor()
                }.byRequest()
            }
        }
    }

    void "can deal with platform upgrades"() {
        given:
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'bar', version: '1.0')
                constraint(group: 'org', artifact: 'foo', version: '1.0', forSubgraph: true)
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
                    inheritConstraints()
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
                    constraint('org:bar:1.0').byConstraint()
                    constraint('org:foo:1.0', 'org:foo:2.0').byConstraint().byConflictResolution("between versions 2.0 and 1.0")
                }.byConflictResolution("between versions 2.0 and 1.0")
                edge('org:bar', 'org:bar:1.0') {
                    module('org:foo:2.0')
                    module('org:platform:2.0').byRequest()
                }.byRequest()
            }
        }
    }

    def "multiple inherited subgraph constraints that target the same module are conflict resolved"() {
        given:
        repository {
            'org:platform-a:1.0'() {
                constraint(group: 'org', artifact: 'foo', version: '1.0', forSubgraph: true)
            }
            'org:platform-b:1.0'() {
                constraint(group: 'org', artifact: 'foo', version: '2.0', forSubgraph: true)
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:platform-a:1.0') {
                    inheritConstraints()
                }
                conf('org:platform-b:1.0') {
                    inheritConstraints()
                }
                conf('org:foo')
            }           
        """

        when:
        repositoryInteractions {
            'org:platform-a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:platform-b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:1.0' {
                expectGetMetadata()
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
                module('org:platform-a:1.0') {
                    constraint('org:foo:{require 1.0; subgraph}', 'org:foo:2.0').byConflictResolution("between versions 2.0 and 1.0")
                }
                module('org:platform-b:1.0') {
                    constraint('org:foo:{require 2.0; subgraph}', 'org:foo:2.0').byConstraint()
                }
                edge('org:foo', 'org:foo:2.0').byRequest()
            }
        }
    }

    def "a module from which constraints are inherited can itself be influenced by constraints inherited form elsewhere"() {
        given:
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'foo', version: '1.0', forSubgraph: true)
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
                    inheritConstraints()
                }
                conf('org:baz:1.0') {
                    inheritConstraints()
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
                    constraint('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byConstraint()
                }
                module('org:baz:1.0') {
                    module('org:bar:1.0') {
                        edge('org:foo:2.0', 'org:foo:1.0').byAncestor()
                    }
                }
            }
        }
    }

    def "constraint inheritance can be consumed from metadata"() {
        given:
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'bar', version: '1.0')
                constraint(group: 'org', artifact: 'foo', version: '1.0', forSubgraph: true)
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
            'org:baz:1.0' {
                dependsOn(group: 'org', artifact: 'platform', version: '1.0', inheritConstraints: true)
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
                        constraint('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byConstraint()
                    }
                    edge('org:bar', 'org:bar:1.0') {
                        byRequest()
                        edge('org:foo:2.0', 'org:foo:1.0').byAncestor()
                    }
                }
            }
        }
    }
}
