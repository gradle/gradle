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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

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
        failure.assertHasCause """Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:foo'
   Constraint path ':test:unspecified' --> 'org:platform-a:1.0' (runtime) --> 'org:foo:{strictly 1.0}'
   Constraint path ':test:unspecified' --> 'org:platform-b:1.0' (runtime) --> 'org:foo:{strictly 2.0}'"""
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
}
