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
import spock.lang.Unroll

class DependenciesAttributesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        buildFile << """
            def CUSTOM_ATTRIBUTE = Attribute.of('custom', String)
            dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)
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
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        where:
        attributeValue | expectedVariant | expectedAttributes
        'c1'           | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', custom: 'c1']
        'c2'           | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', custom: 'c2']
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "Merges consumer configuration attributes with dependency attributes"() {
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
            configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "java-api"))

            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
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
                    configuration = 'api'
                    variant('api', ['org.gradle.status': DependenciesAttributesIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-api', custom: 'c1'])
                }
            }
        }
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "Fails resolution because consumer configuration attributes and dependency attributes conflict"() {
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
            configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "java-api"))

            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Unable to find a matching configuration of org:test:1.0:
  - Configuration 'api':
      - Required custom 'c2' and found incompatible value 'c1'.
      - Found org.gradle.status '${defaultStatus()}' but wasn't required.
      - Required org.gradle.usage 'java-api' and found compatible value 'java-api'.
  - Configuration 'runtime':
      - Required custom 'c2' and found compatible value 'c2'.
      - Found org.gradle.status '${defaultStatus()}' but wasn't required.
      - Required org.gradle.usage 'java-api' and found incompatible value 'java-runtime'""")
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    @Unroll("Selects variant #expectedVariant using custom attribute value #dependencyValue overriding configuration attribute #configurationValue")
    def "dependency attribute value overrides configuration attribute"() {
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
            configurations.conf.attributes.attribute(CUSTOM_ATTRIBUTE, '$configurationValue')

            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, '$dependencyValue')
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
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        where:
        configurationValue | dependencyValue | expectedVariant | expectedAttributes
        'c2'               | 'c1'            | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', custom: 'c1']
        'c1'               | 'c2'            | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', custom: 'c2']
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    @Unroll("Selects variant #expectedVariant using custom attribute value #dependencyValue overriding configuration attribute #configurationValue using dependency constraint")
    def "dependency attribute value overrides configuration attribute using dependency constraint"() {
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
            configurations.conf.attributes.attribute(CUSTOM_ATTRIBUTE, '$configurationValue')

            dependencies {
                constraints {
                    conf('org:test:1.0') {
                       attributes {
                          attribute(CUSTOM_ATTRIBUTE, '$dependencyValue')
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
                edge('org:test', 'org:test:1.0') {
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
                module('org:test:1.0') {
                    configuration = expectedVariant
                    variant(expectedVariant, expectedAttributes)
                }
            }
        }

        where:
        configurationValue | dependencyValue | expectedVariant | expectedAttributes
        'c2'               | 'c1'            | 'api'           | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', custom: 'c1']
        'c1'               | 'c2'            | 'runtime'       | ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', custom: 'c2']
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "Fails resolution because consumer configuration attributes and constraint attributes conflict"() {
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
            configurations.conf.attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "java-api"))

            dependencies {
                constraints {
                    conf('org:test:1.0') {
                        attributes {
                            attribute(CUSTOM_ATTRIBUTE, 'c2')
                        }
                    }
                }
                conf 'org:test'
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Unable to find a matching configuration of org:test:1.0:
  - Configuration 'api':
      - Required custom 'c2' and found incompatible value 'c1'.
      - Found org.gradle.status '${defaultStatus()}' but wasn't required.
      - Required org.gradle.usage 'java-api' and found compatible value 'java-api'.
  - Configuration 'runtime':
      - Required custom 'c2' and found compatible value 'c2'.
      - Found org.gradle.status '${defaultStatus()}' but wasn't required.
      - Required org.gradle.usage 'java-api' and found incompatible value 'java-runtime'""")
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "Fails resolution because dependency attributes and constraint attributes conflict"() {
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
                constraints {
                    conf('org:test:1.0') {
                        attributes {
                            attribute(CUSTOM_ATTRIBUTE, 'c2')
                        }
                    }
                }
                conf('org:test') {
                   attributes {
                      attribute(CUSTOM_ATTRIBUTE, 'c1')
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Cannot choose between 'org:test' and 'org:test:1.0' because they require a different value for attribute 'custom':
  - Dependency 'org:test' wants value 'c1'
  - Constraint 'org:test:1.0' wants value 'c2'""")
    }

    static Closure<String> defaultStatus() {
        { -> GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release' }
    }
}
