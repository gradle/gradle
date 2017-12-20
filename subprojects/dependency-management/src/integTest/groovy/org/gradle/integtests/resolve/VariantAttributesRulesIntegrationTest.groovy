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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import spock.lang.Unroll

class VariantAttributesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {
    @Override
    String getTestConfiguration() { variantToTest }

    /**
     * Does the published metadata provide variants with attributes? Eventually all metadata should do that.
     * For Ivy and Maven POM metadata, the variants and attributes should be derived from configurations and scopes.
     */
    boolean getPublishedModulesHaveAttributes() { gradleMetadataEnabled }

    String getVariantToTest() {
        if (gradleMetadataEnabled || useIvy()) {
            'customVariant'
        } else {
            'compile'
        }
    }

    def setup() {
        repository {
            'org.test:moduleA:1.0'() {
                variant 'customVariant', [format: 'custom']
                dependsOn('org.test:moduleB:1.0')
            }
        }

        buildFile << """
            def testAttribute = Attribute.of("TEST_ATTRIBUTE", String)
            def formatAttribute = Attribute.of('format', String)

            configurations { $variantToTest { attributes { attribute(formatAttribute, 'custom') } } }
            
            dependencies {
                $variantToTest group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }
        """
    }

    def "can add attributes"() {
        given:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant("$variantToTest") { 
                            withAttributes {
                                attribute(formatAttribute, "custom")
                            }
                        }
                    }
                }
            }
        """

        repository {
            'org.test:moduleB:1.0' {
                variant 'customVariant', [:]
            }
        }

        when:
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module("org.test:moduleB:1.0")
                }
            }
        }
    }

    def "can override attributes"() {
        given:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant("$variantToTest") { 
                            withAttributes {
                                attribute(formatAttribute, "custom")
                            }
                        }
                    }
                }
            }
        """

        repository {
            'org.test:moduleB:1.0' {
                variant 'customVariant', [format: 'will be overriden']
            }
        }

        when:
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module("org.test:moduleB:1.0")
                }
            }
        }
    }

    @Unroll
    def "can disambiguate variants to select #selectedVariant"() {
        given:
        buildFile << """
            configurations {
                ${variantToTest}.attributes.attribute(testAttribute, "select")
            }

            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant('$selectedVariant') { 
                            withAttributes {
                                attribute(testAttribute, "select")
                            }
                        }
                    }
                }
            }
        """

        repository {
            'org.test:moduleB:1.0' {
                variant ('customVariant1') {
                    attribute 'format', 'custom'
                    artifact 'variant1'
                }
                variant ('customVariant2') {
                    attribute 'format', 'custom'
                    artifact 'variant2'
                }
            }
        }

        when:
        // @RequiredFeatures not compatible with @Unroll at method level
        if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
            repositoryInteractions {
                'org.test:moduleA:1.0' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                'org.test:moduleB:1.0'() {
                    expectGetMetadata()
                    expectGetVariantArtifacts(selectedVariant)
                }
            }
        }

        then:
        // @RequiredFeatures not compatible with @Unroll at method level
        if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
            succeeds 'checkDep'

            def variantToTest = variantToTest
            resolve.expectGraph {
                root(':', ':test:') {
                    module("org.test:moduleA:1.0:$variantToTest") {
                        module("org.test:moduleB:1.0") {
                            artifact(classifier: (selectedVariant - 'custom').toLowerCase())
                        }
                    }
                }
            }
        }

        where:
        selectedVariant << ['customVariant1', 'customVariant2']
    }

}
