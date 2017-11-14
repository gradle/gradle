/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.RequiresFeatureEnabled

@RequiresFeatureEnabled
class StrictDependenciesResolveIntegrationTest extends AbstractStrictDependenciesIntegrationTest {
    def "should not downgrade dependency version when an external transitive dependency has strict version"() {
        given:
        repository {
            'org:foo' {
                '15'()
                '17'()
            }
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '15', rejects: [']15,)'])
            }
        }

        buildFile << """
            $repository

            configurations {
                conf
            }
            dependencies {
                conf 'org:foo:17'
                conf 'org:bar:1.0'
            }                       
        """

        when:
        repositoryInteractions {
            'org:foo:17' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause('Cannot find a version of \'org:foo\' that satisfies the constraints: prefers 17, prefers 15, rejects ]15,)')

    }

    void "should pass if strict version ranges overlap using external dependencies"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
                '1.1'()
                '1.2'()
                '1.3'()
            }
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '[1.1,1.3]', rejects: [']1.3,)'])
            }
        }

        buildFile << """
            $repository

            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    version { strictly '[1.0,1.2]' }
                }
                conf 'org:bar:1.0'
            }
                         
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '1.2' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                '1.3' {
                    expectGetMetadata()
                }
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:[1.0,1.2]", "org:foo:1.2")
                edge('org:bar:1.0', 'org:bar:1.0') {
                    edge("org:foo:[1.1,1.3]", "org:foo:1.2")
                }
            }
        }
    }


    def "should fail if 2 strict versions disagree (external)"() {
        given:
        repository {
            'org:foo:15'()
            'org:foo:17'()
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '15', rejects: [']15,)'])
            }
        }

        buildFile << """
            $repository

            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    version {
                       strictly '17'
                    }
                }
                conf 'org:bar:1.0'
            }                       
        """

        when:
        repositoryInteractions {
            'org:foo:17' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }

        fails ':checkDeps'

        then:
        failure.assertHasCause('Cannot find a version of \'org:foo\' that satisfies the constraints: prefers 17, rejects ]17,), prefers 15, rejects ]15,)')

    }
}
