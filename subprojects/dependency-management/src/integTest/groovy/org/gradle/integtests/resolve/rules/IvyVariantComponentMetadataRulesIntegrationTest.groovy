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

package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeatures([
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy"),
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false"),
])
class IvyVariantComponentMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest implements ComponentMetadataRulesSupport {

    @Override
    boolean isAddVariantDerivationRuleForIvy() { false }

    def "opt-into variant aware dependency resolution if variant rules are added to ivy configuration"() {
        given:
        repository {
            'org.test:projectA:1.0' {
                variant 'customConf', [:] // in ivy, this is only a configuration
            }
        }

        when:
        buildFile << """
            dependencies {
                conf group: 'org.test', name: 'projectA', version: '1.0'
                components {
                    withModule('org.test:projectA') {
                        withVariant('compile') { }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:projectA:1.0' { expectResolve() }
        }


        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                // we have a variant rule but did not change attributes, no attribute matching opt-in yet
                module("org.test:projectA:1.0:default")
            }
        }

        when:
        buildFile << """
            dependencies {
                conf group: 'org.test', name: 'projectA', version: '1.0'
                components {
                    withModule('org.test:projectA') {
                        withVariant('compile') { 
                            // attribute rule that does not actually set attributes
                            attributes {}
                        }
                    }
                }
            }
        """
        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                // 'compile' is selected (it has no attributes, but is the only variant)
                module("org.test:projectA:1.0:compile")
            }
        }

        when:
        buildFile << """
            def a1 = Attribute.of('a1', String)
            configurations.conf {
                attributes { attribute(a1, 'select') }
            }
            dependencies {
                components {
                    withModule('org.test:projectA') {
                        withVariant('compile') { 
                            attributes { attribute(a1, 'select') }
                        }
                        withVariant('runtime') { 
                            attributes { attribute(a1, 'no-select') }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                // 'compile' is selected by attribute matching
                module("org.test:projectA:1.0:compile")
            }
        }

        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:projectA') {
                        withVariant('customConf') { 
                            attributes { attribute(a1, 'select') }
                        }
                    }
                }
            }
        """

        then:
        fails 'checkDep'
        failure.assertHasCause """Cannot choose between the following variants of org.test:projectA:1.0:
  - compile
  - customConf
All of them match the consumer attributes:
  - Variant 'compile' capability org.test:projectA:1.0:
      - Unmatched attribute:
          - Found org.gradle.status 'integration' but wasn't required.
      - Compatible attribute:
          - Required a1 'select' and found compatible value 'select'.
  - Variant 'customConf' capability org.test:projectA:1.0:
      - Unmatched attribute:
          - Found org.gradle.status 'integration' but wasn't required.
      - Compatible attribute:
          - Required a1 'select' and found compatible value 'select'."""
    }

    def "local explicit configuration selection wins over attribute matching"() {
        given:
        repository {
            'org.test:projectA:1.0'()
        }

        when:
        buildFile << """
            def a1 = Attribute.of('a1', String)
            configurations.conf {
                attributes { attribute(a1, 'select') }
            }
            dependencies {
                conf group: 'org.test', name: 'projectA', version: '1.0', configuration: 'runtime'
                components {
                    withModule('org.test:projectA') {
                        withVariant('compile') { 
                            attributes { attribute(a1, 'select') }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:projectA:1.0' { expectResolve() }
        }

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                // explicit selection of 'runtime', attribute matching is not used
                module("org.test:projectA:1.0:runtime")
            }
        }
    }

    def "attribute matching wins over published explicit configuration selection"() {
        given:
        repository {
            'org.test:projectA:1.0' {
                dependsOn group: 'org.test', artifact: 'projectB', version: '1.0', conf: '*->another'
            }
            'org.test:projectB:1.0'() {
                variant 'another', [:]
            }
        }

        when:
        buildFile << """
            def a1 = Attribute.of('a1', String)
            configurations.conf {
                attributes { attribute(a1, 'select') }
            }
            dependencies {
                conf group: 'org.test', name: 'projectA', version: '1.0'
                components {
                    all {
                        withVariant('compile') { 
                            attributes { attribute(a1, 'select') }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:projectA:1.0' { expectResolve() }
            'org.test:projectB:1.0' { expectResolve() }
        }

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:projectA:1.0") {
                    // variant('default', ['org.gradle.status': 'integration']) <- override by variant matching
                    variant('compile', ['org.gradle.status': 'integration', 'a1': 'select'])
                    module("org.test:projectB:1.0") {
                        // variant('another', ['org.gradle.status': 'integration'])  <- override by variant matching
                        variant('compile', ['org.gradle.status': 'integration', 'a1': 'select'])
                    }
                }
            }
        }
    }

}
