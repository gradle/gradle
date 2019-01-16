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

package org.gradle.integtests.resolve.capabilities

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeatures(
    [@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value="true")]
)
class PublishedCapabilitiesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can consume published capabilities"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5' {
                variant("runtime") {
                    capability('cglib', 'cglib-nodep', '3.2.5')
                    capability('cglib', 'cglib', '3.2.5')
                }
            }
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.5' {
                expectGetMetadata()
            }
            'cglib:cglib:3.2.5' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Cannot choose between cglib:cglib-nodep:3.2.5 and cglib:cglib:3.2.5 because they provide the same capability: cglib:cglib:3.2.5")
    }

    def "can detect conflict with capability in different versions and upgrade automatically to latest version"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.4' {
                variant("runtime") {
                    capability('cglib', 'cglib-nodep', '3.2.4')
                    capability('cglib', 'cglib', '3.2.4')
                }
            }
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.4"
               conf "cglib:cglib:3.2.5"
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.4' {
                expectGetMetadata()
            }
            'cglib:cglib:3.2.5' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('cglib:cglib-nodep:3.2.4', 'cglib:cglib:3.2.5')
                    .byConflictResolution('latest version of capability cglib:cglib')
                module('cglib:cglib:3.2.5')
            }
        }
    }

    def "can detect conflict between local project and capability from external dependency"() {
        given:
        repository {
            'org:test:1.0' {
                variant("runtime") {
                    capability('org', 'test', '1.0')
                    capability('org', 'capability', '1.0')
                }
            }
        }

        buildFile << """
            apply plugin: 'java-library'
            
            configurations.api.outgoing {
                capability 'org:capability:1.0'
            }

            dependencies {
                conf 'org:test:1.0'
            }
            
            configurations {
                conf.extendsFrom(api)
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Cannot choose between :test:unspecified and org:test:1.0 because they provide the same capability: org:capability:1.0")
    }

    /**
     * This test illustrates that published modules can declare capabilities, which are then discovered
     * as we visit the graph. And if no published module declares a preference, then build should fail.
     */
    def "fails with reasonable error message if no module express preference for conflict of modules that publish the same capability"() {
        given:
        repository {
            'org:testA:1.0' {
                variant('runtime') {
                    capability('org', 'testA', '1.0')
                    capability('cap')
                }
            }
            'org:testB:1.0' {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                    capability('cap')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testA:1.0'
                conf 'org:testB:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:testA:1.0' {
                expectGetMetadata()
            }
            'org:testB:1.0' {
                expectGetMetadata()
            }
        }
        fails ":checkDeps"

        then:
        failure.assertHasCause("Cannot choose between org:testA:1.0 and org:testB:1.0 because they provide the same capability: org.test:cap:1.0")
    }

    def "considers all candidates for conflict resolution"() {
        given:
        repository {
            'org:testA:1.0' {
                variant('runtime') {
                    capability('org', 'testA', '1.0')
                    capability('org', 'cap', '1')
                }
            }
            'org:testB:1.0' {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                    capability('org', 'cap', '4')
                }
            }
            'org:testC:1.0' {
                dependsOn('org:testCC:1.0')
            }
            'org:testD:1.0' {
                dependsOn('org:testA:1.0') // must have a dependency on an evicted edge
            }
            'org:testCC:1.0' {
                variant('runtime') {
                    capability('org', 'testCC', '1.0')
                    capability('org', 'cap', '2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testA:1.0'
                conf 'org:testB:1.0'
                conf 'org:testC:1.0'
                conf 'org:testD:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:testA:1.0' {
                expectGetMetadata()
            }
            'org:testB:1.0' {
                expectResolve() // has the highest capability version
            }
            'org:testC:1.0' {
                expectResolve()
            }
            'org:testD:1.0' {
                expectResolve()
            }
            'org:testCC:1.0' {
                expectGetMetadata()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:testA:1.0', 'org:testB:1.0')
                    .byConflictResolution('latest version of capability org:cap')
                module('org:testB:1.0')
                module('org:testC:1.0') {
                    edge('org:testCC:1.0', 'org:testB:1.0')
                        .byConflictResolution('latest version of capability org:cap')
                }
                module('org:testD:1.0') {
                    edge('org:testA:1.0', 'org:testB:1.0')
                        .byConflictResolution('latest version of capability org:cap')
                }
            }
        }

    }

}
