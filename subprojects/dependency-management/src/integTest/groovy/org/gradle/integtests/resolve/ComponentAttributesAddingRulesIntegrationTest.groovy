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

class ComponentAttributesAddingRulesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "component attributes added in component metadata rules are honored"() {
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
                            attribute quality, 'canary'
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
            }
        }

        then:
        fails ':checkDeps'
        failure.assertHasCause("Unable to find a matching configuration of org.test:module:1.0:")
        failure.assertThatCause(containsNormalizedString("Required quality 'qa' and found incompatible value 'canary'"))
    }
}
