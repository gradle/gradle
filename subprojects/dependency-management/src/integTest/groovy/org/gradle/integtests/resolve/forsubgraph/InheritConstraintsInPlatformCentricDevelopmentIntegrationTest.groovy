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
import org.gradle.test.fixtures.file.TestFile

@RequiredFeatures([
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")]
)
class InheritConstraintsInPlatformCentricDevelopmentIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        resolve.withStrictReasonsCheck()
    }

    private TestFile singleLibraryBuildFile() {
        buildFile << """
            dependencies {
                conf('org:platform:1.+') {
                    inheritConstraints()
                }
                conf('org:bar')
            }
        """
    }

    private void initialRepository() {
        repository {
            'org:platform:1.0'() {
                constraint(group: 'org', artifact: 'bar', version: '2.0', forSubgraph: true)
                constraint(group: 'org', artifact: 'foo', version: '3.0', rejects: ['3.1', '3.2'], forSubgraph: true)
            }
            'org:foo' {
                '3.0'()
                '3.1'() // bad version
                '3.2'() // bad version
            }
            'org:bar:2.0'() {
                dependsOn 'org:foo:3.1'
            }
        }
    }

    private void updatedRepository() {
        initialRepository()
        repository {
            'org:platform:1.1'() {
                constraint(group: 'org', artifact: 'bar', version: '2.0', forSubgraph: true)
                constraint(group: 'org', artifact: 'foo', version: '3.1.1', rejects: ['3.1', '3.2'], forSubgraph: true)
            }
            'org:foo' {
                '3.1.1'()
            }
        }
    }

    void "(1) all future releases of org:foo:3.0 are bad and the platform enforces 3.0"() {
        initialRepository()
        singleLibraryBuildFile()

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
            }
            'org:platform:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:3.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:platform:1.+', 'org:platform:1.0') {
                    constraint('org:bar:{require 2.0; subgraph}', 'org:bar:2.0').byConstraint()
                    constraint('org:foo:{require 3.0; reject 3.1 & 3.2; subgraph}', 'org:foo:3.0').byConstraint()
                }
                edge('org:bar', 'org:bar:2.0') {
                    edge('org:foo:3.1', 'org:foo:3.0').byAncestor()
                }.byRequest()
            }
        }
    }

    void "(2) org:foo:3.1.1 and platform upgrade 1.1 are release"() {
        updatedRepository()
        singleLibraryBuildFile()

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
            }
            'org:platform:1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:3.1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:platform:1.+', 'org:platform:1.1') {
                    constraint('org:bar:{require 2.0; subgraph}', 'org:bar:2.0').byConstraint()
                    constraint('org:foo:{require 3.1.1; reject 3.1 & 3.2; subgraph}', 'org:foo:3.1.1').byConstraint()
                }
                edge('org:bar', 'org:bar:2.0') {
                    edge('org:foo:3.1', 'org:foo:3.1.1').byAncestor()
                }.byRequest()
            }
        }
    }

    void "(3) library developer has issues with org:foo:3.1.1 and overrides platform decision with 3.2 which fails due to reject"() {
        updatedRepository()
        singleLibraryBuildFile()
        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:3.2')
                }
            }
        """

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
            }
            'org:platform:1.1' {
                expectGetMetadata()
            }
            'org:bar:2.0' {
                expectGetMetadata()
            }
            'org:foo:3.1.1' {
                expectGetMetadata()
            }
            'org:foo:3.2' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:bar:2.0' --> 'org:foo:3.1'
   Constraint path ':test:unspecified' --> 'org:platform:1.1' --> 'org:foo:{require 3.1.1; reject 3.1 & 3.2; subgraph}'
   Constraint path ':test:unspecified' --> 'org:foo:3.2'"""
    }

    void "(4) library developer has issues with org:foo:3.1.1 and forces an override of the platform decision with forSubgraph"() {
        updatedRepository()
        singleLibraryBuildFile()
        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:3.2') { 
                        version { forSubgraph() }
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
                expectGetArtifact()
            }
            'org:platform:1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:bar:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:3.2' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{require 3.2; subgraph}', 'org:foo:3.2').byConstraint()
                edge('org:platform:1.+', 'org:platform:1.1') {
                    constraint('org:bar:{require 2.0; subgraph}', 'org:bar:2.0').byConstraint()
                    constraint('org:foo:{require 3.1.1; reject 3.1 & 3.2; subgraph}', 'org:foo:3.2')
                }
                edge('org:bar', 'org:bar:2.0') {
                    edge('org:foo:3.1', 'org:foo:3.2').byAncestor()
                }.byRequest()
            }
        }
    }

    void "(5) if two libraries are combined without agreeing on an override, the original platform constraint is brought back"() {
        updatedRepository()
        settingsFile << "\ninclude 'recklessLibrary', 'secondLibrary'"
        buildFile << """
            project(':recklessLibrary') {
                configurations { conf }
                dependencies {
                    conf('org:platform:1.+') {
                        inheritConstraints()
                    }
                    conf('org:bar')
                    constraints {
                        conf('org:foo:3.2') { 
                            version { forSubgraph() } // ignoring platform's reject
                        }
                    }
                }
            }
            project(':secondLibrary') {
                configurations { conf }
                dependencies {
                    conf('org:platform:1.+') {
                        inheritConstraints()
                    }
                    conf('org:bar')
                }
            }
            dependencies {
                conf(project(path: ':recklessLibrary', configuration: 'conf'))
                conf(project(path: ':secondLibrary', configuration: 'conf'))
            }
        """

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
            }
            'org:platform:1.1' {
                expectGetMetadata()
            }
            'org:bar:2.0' {
                expectGetMetadata()
            }
            'org:foo:3.1.1' {
                expectGetMetadata()
            }
            'org:foo:3.2' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'test:recklessLibrary:unspecified' --> 'org:bar:2.0' --> 'org:foo:3.1'
   Constraint path ':test:unspecified' --> 'test:recklessLibrary:unspecified' --> 'org:platform:1.1' --> 'org:foo:{require 3.1.1; reject 3.1 & 3.2; subgraph}'
   Constraint path ':test:unspecified' --> 'test:recklessLibrary:unspecified' --> 'org:foo:{require 3.2; subgraph}'"""
    }
}
