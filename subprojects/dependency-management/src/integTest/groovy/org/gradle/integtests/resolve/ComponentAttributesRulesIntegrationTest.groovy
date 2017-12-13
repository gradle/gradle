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
import spock.lang.Ignore
import spock.lang.Unroll

@RequiredFeatures(
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
)
class ComponentAttributesRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Unroll
    def "succeeds if attribute is added to component via component metadata rules (fix = #fixApplied)"() {
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
                        withAttributes {
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
    }

    // This is eventually what we want, but version listing is not wired properly for this
    @Ignore
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
                conf.attributes.attribute(quality, 'qa')
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                components {
                    withModule('org.test:module') { module ->                   
                        withAttributes {
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
                module('org.test:module:1.1')
            }
        }

    }


}
