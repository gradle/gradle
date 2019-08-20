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

class SubgraphVersionConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        resolve.withStrictReasonsCheck()
    }

    def "can downgrade version"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                   version { forSubgraph() }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byRequest()
                module('org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.0').byAncestor()
                }
            }
        }
    }

    def "can use dependency constraint to downgrade version"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:1.0') {
                       version { forSubgraph() }
                    }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byConstraint()
                module('org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.0').byAncestor()
                }
            }
        }
    }

    def "a forSubgraph constraint wins over a nested forSubgraph constraint"() {
        boolean publishedConstraintsSupported = gradleMetadataPublished

        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                constraint(group: 'org', artifact: 'c', version: '2.0', forSubgraph: true)
            }
            'org:b:1.0' {
                dependsOn 'org:c:3.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
            'org:c:3.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                constraints {
                    conf('org:c:1.0') { version { forSubgraph() } }
                }
            }    
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        edge('org:c:3.0', 'org:c:1.0').byAncestor()
                    }
                    if (publishedConstraintsSupported) {
                        constraint('org:c:{require 2.0; subgraph}', 'org:c:1.0')
                    }
                }
                constraint('org:c:{require 1.0; subgraph}', 'org:c:1.0').byConstraint()
            }
        }
    }

    def "identical forSubgraph constraints can co-exist in a graph"() {
        boolean publishedConstraintsSupported = gradleMetadataPublished

        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                constraint(group: 'org', artifact: 'c', version: '1.0', forSubgraph: true)
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                constraints {
                    conf('org:c:1.0') { version { forSubgraph() } }
                }
            }    
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        edge('org:c:2.0', 'org:c:1.0').byAncestor()
                    }
                    if (publishedConstraintsSupported) {
                        constraint('org:c:{require 1.0; subgraph}', 'org:c:1.0')
                    }
                }
                constraint('org:c:{require 1.0; subgraph}', 'org:c:1.0').byConstraint()
            }
        }
    }

    def "conflicting version constraints are conflict resolved"() {
        boolean subgraphConstraintsSupported = gradleMetadataPublished

        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0', forSubgraph: true)
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                conf('org:c:2.0')
            }    
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        module('org:c:2.0')
                    }
                    if (subgraphConstraintsSupported) {
                        edge('org:c:{require 1.0; subgraph}', 'org:c:2.0').byAncestor()
                    } else {
                        edge('org:c:1.0', 'org:c:2.0')
                    }

                }
                module('org:c:2.0').byRequest().byConflictResolution("between versions 2.0 and 1.0")
            }
        }
    }

    def "forSubgraph is consumed from Gradle metadata"() {
        given:
        // If we do not use Gradle metadata, information is missing and we get a different result
        String cResult = gradleMetadataPublished ? 'org:c:1.0' : 'org:c:2.0'

        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0', forSubgraph: true)
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
            }    
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            "$cResult" {
                expectGetMetadata()
                expectGetArtifact()
            }
            if (cResult == 'org:c:2.0') {
                'org:c:1.0' {
                    expectGetMetadata()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:a:1.0') {
                    module('org:b:1.0') {
                        edge('org:c:2.0', cResult).byRequest()
                    }
                    if (cResult == 'org:c:1.0') {
                        edge('org:c:{require 1.0; subgraph}', cResult).byAncestor()
                    } else {
                        edge('org:c:1.0', cResult).byConflictResolution("between versions 2.0 and 1.0")
                    }
                }
            }
        }
    }

    def "forSubgraph from selected and later evicted modules are ignored"() {
        given:
        repository {
            'org:a:1.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0', forSubgraph: true)
            }
            'org:a:2.0' {
                dependsOn(group: 'org', artifact: 'b', version: '1.0')
                dependsOn(group: 'org', artifact: 'c', version: '1.0')
            }
            'org:b:1.0' {
                dependsOn 'org:c:2.0'
            }
            'org:c:1.0'()
            'org:c:2.0'()

            'org:x:1.0' {
                dependsOn 'org:y:1.0'
            }
            'org:y:1.0' {
                dependsOn 'org:a:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:a:1.0')
                conf('org:x:1.0')
            }    
        """

        when:
        repositoryInteractions {
            'org:a:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:b:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:c:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }

            'org:x:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:y:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }

            'org:a:1.0' {
                expectGetMetadata()
            }
            'org:c:1.0' {
                expectGetMetadata()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:a:1.0', 'org:a:2.0') {
                    module('org:b:1.0') {
                        module('org:c:2.0').byRequest()
                    }
                    edge('org:c:1.0', 'org:c:2.0').byConflictResolution("between versions 2.0 and 1.0")
                }.byConflictResolution("between versions 2.0 and 1.0")
                module('org:x:1.0') {
                    module('org:y:1.0') {
                        module('org:a:2.0').byRequest()
                    }
                }
            }
        }
    }

    def "can bring back a rejected version"() {
        String publishedFooDependencyVersion = gradleMetadataPublished ? '{require 2.0; reject 1.0}' : '2.0'

        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '2.0', rejects: ['1.0'])
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:1.0') {
                       version { forSubgraph() }
                    }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byConstraint()
                module('org:bar:1.0') {
                    edge("org:foo:$publishedFooDependencyVersion", 'org:foo:1.0').byAncestor()
                }
            }
        }
    }

    def "can downgrade a version range"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:foo:2.1'()
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '[2.0,3.0)')
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:1.0') {
                       version { forSubgraph() }
                    }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            // no version listing needed!
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byConstraint()
                module('org:bar:1.0') {
                    edge("org:foo:[2.0,3.0)", 'org:foo:1.0').byAncestor()
                }
            }
        }
    }

    def "can downgrade to a local project"() {
        given:
        repository {
            'org:foo:2.0'()
            'org:bar:1.0' {
               dependsOn 'org:foo:2.0'
            }
        }

        settingsFile << "\ninclude 'foo'"
        buildFile << """
            project(':foo') {
                configurations.create('default')
                group = 'org'
                version = '1.0'
            }
            dependencies {
                constraints {
                    conf('org:foo:1.0') {
                       version { forSubgraph() }
                    }
                }
                conf('org:bar:1.0')
                conf(project(':foo'))
            }           
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{require 1.0; subgraph}', 'project :foo', 'org:foo:1.0').byConstraint()
                module('org:bar:1.0') {
                    edge('org:foo:2.0', 'project :foo', 'org:foo:1.0') {}.byAncestor()
                }
                project(':foo', 'org:foo:1.0') {
                    configuration = 'default'
                    noArtifacts()
                }.byRequest()
            }
        }
    }

    def "multiple subgraph constraint targeting the same module on the same level are conflict resolved"() {
        given:
        repository {
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
            'org:foo:1.0'()
            'org:foo:1.1'()
            'org:foo:2.0'()
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                   version { forSubgraph() }
                }
                conf('org:foo:1.1') {
                   version { forSubgraph() }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:foo:{require 1.0; subgraph}', 'org:foo:1.1').byConflictResolution("between versions 1.1 and 1.0")
                edge('org:foo:{require 1.1; subgraph}', 'org:foo:1.1').byRequest()
                module('org:bar:1.0') {
                    edge('org:foo:2.0', 'org:foo:1.1').byAncestor()
                }
            }
        }
    }

    @RequiredFeatures([
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")]
    )
    def "original version constraint is not ignored if there is another parent"() {
        given:
        repository {
            'org:x1:1.0' {
                dependsOn 'org:bar:1.0'
                constraint(group: 'org', artifact: 'foo', version: '1.0', forSubgraph: true)
            }
            'org:x2:1.0' {
                dependsOn 'org:bar:1.0'
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:x1:1.0')
                conf('org:x2:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:x1:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:x2:1.0' {
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
                module('org:x1:1.0') {
                    module('org:bar:1.0') {
                        module('org:foo:2.0').byRequest()
                    }
                    constraint('org:foo:{require 1.0; subgraph}', 'org:foo:2.0').byConstraint().byConflictResolution("between versions 2.0 and 1.0")
                }
                module('org:x2:1.0') {
                    module('org:bar:1.0')
                }
            }
        }
    }

    @RequiredFeatures([
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")]
    )
    def "can reintroduce a subgraph constraint on the root level"() { // similar to test above, but reintroduces subgraph constraint in build script
        given:
        repository {
            'org:x1:1.0' {
                dependsOn 'org:bar:1.0'
                constraint(group: 'org', artifact: 'foo', version: '1.0', forSubgraph: true)
            }
            'org:x2:1.0' {
                dependsOn 'org:bar:1.0'
            }
            'org:foo:1.0'()
            'org:foo:2.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:2.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:x1:1.0')
                conf('org:x2:1.0')
                constraints { 
                    conf('org:foo:1.0') { version { forSubgraph() } }
                }
            }           
        """

        when:
        repositoryInteractions {
            'org:x1:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:x2:1.0' {
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
                module('org:x1:1.0') {
                    module('org:bar:1.0') {
                        edge('org:foo:2.0', 'org:foo:1.0').byAncestor()
                    }
                    constraint('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byConstraint()
                }
                module('org:x2:1.0') {
                    module('org:bar:1.0')
                }
                constraint('org:foo:{require 1.0; subgraph}', 'org:foo:1.0').byConstraint()
            }
        }
    }
}
