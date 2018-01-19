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
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import spock.lang.Unroll

@RequiredFeatures(
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
)
class ComponentAttributesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Unroll("#outcome if attribute is #mutation via component metadata rule")
    def "check that attribute rules modify the result of dependency resolution"() {
        given:
        repository {
            'org.test:module:1.0'()
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            configurations {
                conf.attributes.attribute(quality, 'qa')
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module') {
                        attributes {
                            attribute quality, ${fixApplied ? '"qa"' : '"canary"'}
                        }
                    }
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                if (fixApplied) {
                    expectGetArtifact()
                }
            }
        }

        then:
        if (fixApplied) {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org.test:module:1.0')
                }
            }
        } else {
            fails ':checkDeps'
            failure.assertHasCause("Unable to find a matching configuration of org.test:module:1.0:")
            failure.assertThatCause(containsNormalizedString("Required quality 'qa' and found incompatible value 'canary'"))
        }

        where:
        fixApplied << [false, true]

        // for description of the test
        outcome << ['fails', 'succeeds']
        mutation << ['not added', 'added']
    }

    @Unroll
    def "variant attributes take precedence over component attributes (component level = #componentLevel)"() {
        given:
        repository {
            'org.test:module:1.0'()
        }
        buildFile << """
            def usage = Attribute.of('org.gradle.usage', String)
            dependencies {
                components {
                    withModule('org.test:module') {                       
                        if ($componentLevel) {
                            attributes { attribute ${attribute}, ${attributeValue} }
                        } else {
                            withVariant('api') { attributes { attribute ${attribute}, ${attributeValue.replace('unknown', 'unknownApiVariant')} } }
                            withVariant('runtime') { attributes { attribute ${attribute}, ${attributeValue.replace('unknown', 'unknownRuntimeVariant')} } }
                        }
                    }
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                if (componentLevel) {
                    expectGetArtifact()
                }
            }
        }

        then:
        if (componentLevel) {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org.test:module:1.0')
                }
            }
        } else {
            fails ':checkDeps'
            failure.assertHasCause("Cannot choose between the following configurations of org.test:module:1.0:")
            failure.assertThatCause(containsNormalizedString("Found org.gradle.usage 'unknownApiVariant' but wasn't required"))
            failure.assertThatCause(containsNormalizedString("Found org.gradle.usage 'unknownRuntimeVariant' but wasn't required"))
        }

        where:
        componentLevel | attribute               | attributeValue
        true           | 'Usage.NAME'            | "'unknown'"
        true           | 'Usage.USAGE_ATTRIBUTE' | "objects.named(Usage, 'unknown')"
        false          | 'Usage.NAME'            | "'unknown'"
        false          | 'Usage.USAGE_ATTRIBUTE' | "objects.named(Usage, 'unknown')"
    }

    def "can use a component metadata rule to infer quality attribute"() {
        given:
        repository {
            'org.test:module:1.0'()
            'org.test:module:1.1'()
            'org.test:module:1.2'()
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            configurations {
                conf {
                   attributes.attribute(quality, 'qa')
                   // TODO: this shouldn't be necessary, because ideally we should consider attributes
                   // during version listing too
                   resolutionStrategy.componentSelection.all { ComponentSelection selection, ComponentMetadata md ->
                      if (md.attributes.getAttribute(quality) != 'qa') {
                         selection.reject('Not approved by QA')
                      }
                   }
                }
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module') { module ->                   
                        attributes {
                            attribute quality, module.id.version=='1.1' ? 'qa' : 'low'
                        }
                    }
                }
                conf 'org.test:module:[1.0,2.0)'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module' {
                expectVersionListing()
            }
            'org.test:module:1.2' {
                expectGetMetadata()
            }
            'org.test:module:1.1' {
                expectResolve()
            }
        }

        then:
        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org.test:module:[1.0,2.0)', 'org.test:module:1.1')
            }
        }

    }

    @Unroll
    def "published component metadata can be overwritten (fix applied = #fixApplied)"() {
        given:
        repository {
            'org.test:module:1.0' {
                attribute 'quality', 'canary'
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            
            configurations {
                conf.attributes.attribute(quality, 'qa')
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module') {
                        if ($fixApplied) {
                           attributes {
                              attribute quality, 'qa'
                           }
                        }
                    }
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                if (fixApplied) {
                    expectGetArtifact()
                }
            }
        }

        then:
        if (fixApplied) {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org.test:module:1.0')
                }
            }
        } else {
            fails ':checkDeps'
            failure.assertHasCause("Unable to find a matching configuration of org.test:module:1.0:")
            failure.assertThatCause(containsNormalizedString("Required quality 'qa' and found incompatible value 'canary'"))
        }

        where:
        fixApplied << [false, true]
    }

    def "can add attributes to variants with existing usage attribute"() {
        given:
        repository {
            'org.test:module:1.0'()
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            
            configurations {
                conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API))
                conf.attributes.attribute(quality, 'qa')
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module') {
                       allVariants {
                           attributes {
                              attribute quality, 'qa'
                           }
                       }
                    }
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.test:module:1.0:api')
            }
        }
    }
}
