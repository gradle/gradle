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

class CapabilitiesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can declare capabilities using a component metadata rule"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5'()
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {
            
                @Override
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    details.allVariants {
                         withCapabilities {
                             addCapability('cglib', 'cglib', details.id.version)
                         }
                     }
                }
            }

            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               components {
                  withModule('cglib:cglib-nodep', CapabilityRule)
               }
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
        def variant = 'runtime'
        if (!isGradleMetadataEnabled() && useIvy()) {
            variant = 'default'
        }
        failure.assertHasCause("""Module 'cglib:cglib-nodep' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib:3.2.5($variant)]""")
        failure.assertHasCause("""Module 'cglib:cglib' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib-nodep:3.2.5($variant)]""")
    }

    def "can detect conflict with capability in different versions and upgrade automatically to latest version"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.4'()
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {
            
                @Override
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    details.allVariants {
                         withCapabilities {
                             addCapability('cglib', 'cglib', details.id.version)
                         }
                     }
                }
            }

            dependencies {
               conf "cglib:cglib-nodep:3.2.4"
               conf "cglib:cglib:3.2.5"
            
               components {
                  withModule('cglib:cglib-nodep', CapabilityRule)
               }
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
            'org:test:1.0'()
        }

        buildFile << """
            apply plugin: 'java-library'
            
            class CapabilityRule implements ComponentMetadataRule {
            
                @Override
                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withCapabilities {
                            addCapability('org', 'capability', '1.0')
                        }
                    }
                }
            }

            configurations.api.outgoing {
                capability 'org:capability:1.0'
            }

            dependencies {
                conf 'org:test:1.0'
                
                components {
                   withModule('org:test', CapabilityRule)
                }
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
        failure.assertHasCause("""Module 'org:test' has been rejected:
   Cannot select module with conflict on capability 'org:capability:1.0' also provided by [:test:unspecified(conf)]""")
    }

    @RequiredFeatures(
        [@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value="true")]
    )
    def "can remove a published capability"() {
        given:
        repository {
            'org:a:1.0' {
                variant('runtime') {
                    capability('test_capability')
                }
            }
            'org:b:1.0' {
                variant('runtime') {
                    capability('test_capability')
                }
            }
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    context.details.allVariants {
                        withCapabilities {
                            removeCapability('org.test', 'test_capability')
                        }
                    }
                }
            }

            dependencies {
                conf 'org:a:1.0'
                conf 'org:b:1.0'
                
                components.all(CapabilityRule)
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectResolve()
            }
            'org:b:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:a:1.0')
                module('org:b:1.0')
            }
        }
    }
}
