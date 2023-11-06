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

package org.gradle.integtests.resolve.capabilities


import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class CapabilitiesConflictResolutionIntegrationTest extends AbstractModuleDependencyResolveTest {

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "reasonable error message when a user rule throws an exception (#rule)"() {
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

            // fix the conflict between modules providing the same capability using resolution rules
            configurations.all {
                resolutionStrategy {
                   capabilitiesResolution.withCapability('org.test:cap') {
                      $rule
                      because "we like testB better"
                   }
                }
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

        failure.assertHasCause(error)

        where:
        rule                               | error
        "throw new NullPointerException()" | "Capability resolution rule failed with an error" // error in user code
        "select('org:testD:1.0')"          | "org:testD:1.0 is not a valid candidate for conflict resolution on capability capability group='org.test', name='cap', version='null': candidates are [org:testA:1.0(runtime), org:testB:1.0(runtime)]"// invalid candidate

    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "can express preference for capabilities declared in published modules (#rule)"() {
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

            // fix the conflict between modules providing the same capability using resolution rules
            configurations.all {
                resolutionStrategy {
                   capabilitiesResolution.withCapability('org.test:cap') {
                      $rule
                      because "we like testB better"
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:testA:1.0' {
                expectGetMetadata()
            }
            'org:testB:1.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:

        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:testA:1.0', 'org:testB:1.0')
                    .byConflictResolution("On capability org.test:cap we like testB better")
                module('org:testB:1.0')
            }
        }

        where:
        rule << [
            "select(candidates.find { it.id.module == 'testB'})",
            "select('org:testB:1.0')",
            "select('org:testB:1.1')", // we are lenient wrt to the version number
        ]
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "can express preference for a certain variant with capabilities declared in published modules"() {
        given:
        repository {
            'org:testB:1.0' {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                }
                variant('runtimeAlt') {
                    capability('org', 'testB', '1.0')
                    capability('special')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testB:1.0'
                conf('org:testB:1.0') {
                    capabilities {
                        requireCapability("org.test:special")
                    }
                }
            }

            // fix the conflict between variants of module providing the same capability using resolution rules
            configurations.all {
                resolutionStrategy {
                   capabilitiesResolution.withCapability('org:testB') {
                      select(candidates.find { it.variantName == 'runtimeAlt'})
                      because "we want runtimeAlt with 'special'"
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:testB:1.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:

        resolve.expectGraph {
            root(":", ":test:") {
                module('org:testB:1.0:runtimeAlt').byConflictResolution("On capability org:testB we want runtimeAlt with 'special'")
//                module('org:testB:1.0:runtimeAlt')
            }
        }
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "expressing a preference for a variant with capabilities declared in a published modules does not evict unrelated variants"() {
        given:
        repository {
            'org:testB:1.0' {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                }
                variant('runtimeAlt') {
                    capability('org', 'testB', '1.0')
                    capability('special')
                }
                variant('runtimeOptional') {
                    capability('optional')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testB:1.0'
                conf('org:testB:1.0') {
                    capabilities {
                        requireCapability("org.test:special")
                    }
                }
                conf('org:testB:1.0') {
                    capabilities {
                        requireCapability("org.test:optional")
                    }
                }
            }

            // fix the conflict between variants of module providing the same capability using resolution rules
            configurations.all {
                resolutionStrategy {
                   capabilitiesResolution.withCapability('org:testB') {
                      select(candidates.find { it.variantName == 'runtimeAlt'})
                      because "we want runtimeAlt with 'special'"
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:testB:1.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:

        resolve.expectGraph {
            root(":", ":test:") {
                module('org:testB:1.0:runtimeAlt').byConflictResolution("On capability org:testB we want runtimeAlt with 'special'")
//                module('org:testB:1.0:runtimeAlt')
                module('org:testB:1.0:runtimeOptional')
            }
        }
    }
}
