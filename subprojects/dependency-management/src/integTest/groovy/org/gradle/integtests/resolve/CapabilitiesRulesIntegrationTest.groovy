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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures

class CapabilitiesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can declare capabilities using a component metadata rule"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5'()
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               components {
                  withModule('cglib:cglib') {
                     withCapabilities {
                         capability('cglib') {
                             providedBy 'cglib:cglib'
                             providedBy 'cglib:cglib-nodep'
                             prefer 'cglib:cglib'
                         }
                     }
                  }
               }
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.5' {
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
                module('cglib:cglib:3.2.5').byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                edge('cglib:cglib-nodep:3.2.5', 'cglib:cglib:3.2.5').byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
            }
        }
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "can remove preference set in published metadata"() {
        given:
        repository {
            'cglib:cglib:3.2.5' {
                capability('cglib') {
                    providedBy 'cglib:cglib'
                    prefer 'cglib:cglib'
                }
            }
            'cglib:cglib-nodep:3.2.5' {
                capability('cglib') {
                    providedBy 'cglib:cglib-nodep'
                    prefer 'cglib:cglib-nodep'
                }
            }
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               components {
                  withModule('cglib:cglib-nodep') {
                     withCapabilities {
                         capability('cglib') {
                             prefer(null)
                             because "reason when preference is null is not used"
                         }
                     }
                  }
               }
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.5' {
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
                module('cglib:cglib:3.2.5').byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                edge('cglib:cglib-nodep:3.2.5', 'cglib:cglib:3.2.5').byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
            }
        }
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "can overwrite the providers of a capability"() {
        given:
        repository {
            'cglib:cglib:3.2.5' {
                capability('cglib') {
                    prefer 'cglib:cglib'
                }
            }
            'cglib:cglib-nodep:3.2.5' {
                capability('cglib') {
                    prefer 'cglib:cglib-nodep'
                }
            }
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               components {
                  withModule('cglib:cglib') {
                     withCapabilities {
                         capability('cglib') {
                             providedBy = ['cglib:cglib', 'cglib:cglib-nodep']
                         }
                     }
                  }
                  withModule('cglib:cglib-nodep') {
                     withCapabilities {
                         capability('cglib') {
                             // sets a different list of modules but it shouldn't interfere
                             // with the ones from cglib:cglib
                             providedBy = ['cglib:cglib']
                         }
                     }
                  }
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
        failure.assertHasCause('Module cglib:cglib:3.2.5 prefers module cglib:cglib for capability \'cglib\' but another module prefers cglib:cglib-nodep')
    }
}
