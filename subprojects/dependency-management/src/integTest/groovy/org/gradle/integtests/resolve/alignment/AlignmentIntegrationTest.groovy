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

package org.gradle.integtests.resolve.alignment

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class AlignmentIntegrationTest extends AbstractModuleDependencyResolveTest {
    def "should align leaves to the same version"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'xml:1.1 -> core:1.1'
            path 'json:1.1 -> core:1.1'
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.1'
            }
        """

        when:
        repositoryInteractions {
            'org:core:1.0' {
                expectGetMetadata()
            }
            'org:xml:1.0' {
                expectGetMetadata()
            }
            'org:core:1.1' {
                expectResolve()
            }
            'org:json:1.1' {
                expectResolve()
            }
            'org:xml:1.1' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:xml:1.0", "org:xml:1.1") {
                    module('org:core:1.1')
                }
                module("org:json:1.1") {
                    module('org:core:1.1')
                }
            }
        }
    }

    def "should align leaf with core"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'xml:1.1 -> core:1.1'
            path 'json:1.1 -> core:1.1'
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.0'
                conf 'org:core:1.1'
            }
        """

        when:
        repositoryInteractions {
            'org:xml:1.0' {
                expectGetMetadata()
            }
            'org:json:1.0' {
                expectGetMetadata()
            }
            'org:core:1.1' {
                expectResolve()
            }
            'org:json:1.1' {
                expectResolve()
            }
            'org:xml:1.1' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:xml:1.0", "org:xml:1.1") {
                    module('org:core:1.1')
                }
                edge("org:json:1.0", "org:json:1.1") {
                    module('org:core:1.1')
                }
                module('org:core:1.1')
            }
        }
    }

    def "shouldn't fail if target alignment version doesn't exist"() {
        repository {
            path 'xml -> core'
            path 'json -> core'
            path 'json:1.1 -> core:1.1'
        }

        given:
        buildFile << """
            dependencies {
                conf 'org:xml:1.0'
                conf 'org:json:1.1'
            }
        """

        when:
        repositoryInteractions {
            'org:core:1.0' {
                expectGetMetadata()
            }
            'org:xml:1.0' {
                expectResolve()
            }
            'org:core:1.1' {
                expectResolve()
            }
            'org:json:1.1' {
                expectResolve()
            }
            'org:xml:1.1' {
                expectGetMetadataMissing()
                if (!GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled) {
                    expectHeadArtifactMissing()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:xml:1.0") {
                    edge('org:core:1.0', 'org:core:1.1').byConflictResolution("between versions 1.0 and 1.1")
                }
                module("org:json:1.1") {
                    module('org:core:1.1')
                }
            }
        }
    }
}
