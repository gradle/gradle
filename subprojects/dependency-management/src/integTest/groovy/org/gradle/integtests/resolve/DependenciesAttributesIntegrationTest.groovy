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
import org.gradle.util.ToBeImplemented
import spock.lang.Ignore
import spock.lang.Unroll

class DependenciesAttributesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        buildFile << """
            def CUSTOM_ATTRIBUTE = Attribute.of('custom', String)
        """
    }

    def "can declare attributes on dependencies"() {
        given:
        repository {
            'org:test:1.0'()
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'test value')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0')
            }
        }

        and:
        outputDoesNotContain("Cannot set attributes for dependency \"org:test:1.0\": it was probably created by a plugin using internal APIs")
    }

    def "can declare attributes on constraints"() {
        given:
        repository {
            'org:test:1.0'()
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:test:1.0') {
                        attributes {
                            attribute(CUSTOM_ATTRIBUTE, 'test value')
                        }
                    }
                }
                conf 'org:test'
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:test', 'org:test:1.0')
                edge('org:test:1.0', 'org:test:1.0')
            }
        }

        and:
        outputDoesNotContain("Cannot set attributes for constraint \"org:test:1.0\": it was probably created by a plugin using internal APIs")
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    @Unroll("Selects variant #expectedVariant using custom attribute value #attributeValue")
    @ToBeImplemented
    @Ignore
    def "attribute value is used during selection"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, '$attributeValue')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0') {
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        where:
        attributeValue | expectedVariant | expectedAttributes
        'c1'           | 'api'           | ['org.gradle.status': 'release', 'org.gradle.usage': 'java-api', custom: 'c1']
        'c2'           | 'runtime'       | ['org.gradle.status': 'release', 'org.gradle.usage': 'java-runtime', custom: 'c2']
    }
}
