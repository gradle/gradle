/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class LazyAttributesIntegrationTest extends AbstractIntegrationSpec {
    def "properties used as attribute values are read lazily"() {
        settingsFile << "rootProject.name = 'TestProject'"

        buildFile << """
            plugins {
                id 'java'
            }

            Property<String> sampleProperty = project.objects.property(String)
            sampleProperty.set("original value")

            configurations {
                sample {
                    visible = false
                    canBeResolved = false
                    assert canBeConsumed

                    attributes {
                        attributeProvider(Usage.USAGE_ATTRIBUTE, sampleProperty.map(value -> objects.named(Usage, value)))
                    }
                }
            }

            sampleProperty.set("new value")
            """.stripIndent()

        expect:
        succeeds "outgoingVariants"
        result.groupedOutput.task(':outgoingVariants').assertOutputContains("""
            --------------------------------------------------
            Variant sample
            --------------------------------------------------

            Capabilities
                - :TestProject:unspecified (default capability)
            Attributes
                - org.gradle.usage = new value""".stripIndent())
    }

    def "providers used as attribute values with mismatched value types fail properly"() {
        buildFile << """
            plugins {
                id 'java'
            }

            Provider<Integer> testProvider = project.provider { 1 }

            configurations {
                sample {
                    visible = false
                    canBeResolved = false
                    assert canBeConsumed
                    extendsFrom(configurations.implementation)

                    attributes {
                        attributeProvider(Usage.USAGE_ATTRIBUTE, testProvider)
                    }
                }
            }
            """.stripIndent()

        expect:
        fails "outgoingVariants"
        failure.assertHasCause("Unexpected type for attribute 'org.gradle.usage' provided. Expected a value of type org.gradle.api.attributes.Usage but found a value of type java.lang.Integer.")
    }

    def "providers used as attribute values with mismatched Attribute types fail properly"() {
        buildFile << """
            plugins {
                id 'java'
            }

            Provider<String> testProvider = project.provider { "test" }

            configurations {
                sample {
                    visible = false
                    canBeResolved = false
                    assert canBeConsumed
                    extendsFrom(configurations.implementation)

                    attributes {
                        attributeProvider(Usage.USAGE_ATTRIBUTE, testProvider.map(value -> objects.named(Category, value)))
                    }
                }
            }
            """.stripIndent()

        expect:
        fails "outgoingVariants"
        failure.assertHasCause("Unexpected type for attribute 'org.gradle.usage' provided. Expected a value of type org.gradle.api.attributes.Usage but found a value of type org.gradle.api.attributes.Category\$Impl.")
    }
}
