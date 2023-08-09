/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.component

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.intellij.lang.annotations.Language

class VariantMatchingFailureIntepreterIntegrationTest extends AbstractIntegrationSpec {
    def "can register a failure interpreter"() {
        buildFile << """
            ${defineCustomInterpreter()}
            ${setupAmbiguousVariantSelectionFailure()}

            dependencies {
                addVariantMatchingFailureInterpreter(new TestVariantMatchingFailureInterpreter())
            }

            configurations.consumer.incoming.files.each { println it }
        """

        expect:
        fails "help", "outgoingVariants", "resolvableConfigurations"
        errorOutput.contains("Could not resolve all files for configuration ':consumer'.")
    }

    def "default JDK mismatch failure interpreter is automatically added by plugin"() {
        buildFile << """
            plugins {
                id 'java-base'
            }

            ${setupAmbiguousVariantSelectionFailure()}

            configurations.consumer.incoming.files.each { println it }
        """

        expect:
        fails "help", "outgoingVariants", "resolvableConfigurations"
        errorOutput.contains("Could not resolve all files for configuration ':consumer'.")
    }

    def "can register a failure interpreter with the java library plugin applied"() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${defineCustomInterpreter()}
            ${setupAmbiguousVariantSelectionFailure()}

            configurations.consumer.incoming.files.each { println it }
        """

        expect:
        fails "help", "outgoingVariants", "resolvableConfigurations"
        errorOutput.contains("Could not resolve all files for configuration ':consumer'.")
    }

    def "can register a failure interpreter via a test suite"() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${defineCustomInterpreter()}
            ${setupAmbiguousVariantSelectionFailure()}

            testing {
                suites {
                    mySuite(JvmTestSuite) {
                        dependencies {
                            addVariantMatchingFailureInterpreter(new TestVariantMatchingFailureInterpreter())
                        }
                    }
                }
            }

            configurations.consumer.incoming.files.each { println it }
        """

        expect:
        fails "help", "outgoingVariants", "resolvableConfigurations"
        errorOutput.contains("Could not resolve all files for configuration ':consumer'.")
    }

    @Language("Groovy")
    private String setupAmbiguousVariantSelectionFailure() {
        return """
            def color = Attribute.of('color', String)
            def shape = Attribute.of('shape', String)

            configurations {
                producer1 {
                    setCanBeConsumed(true)
                    attributes.attribute color, 'blue'
                    attributes.attribute shape, 'round'
                    outgoing {
                        artifact file('a1.jar')
                    }
                }
                producer2 {
                    setCanBeConsumed(true)
                    attributes.attribute color, 'blue'
                    attributes.attribute shape, 'square'
                    outgoing {
                        artifact file('a2.jar')
                    }
                }
                consumer {
                    setCanBeConsumed(false)
                    setCanBeResolved(true)
                    attributes.attribute color, 'blue'

                    // Ensure that the variants added by the `java-library` plugin do not match and aren't considered in Step 1 of variant matching
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                }
            }

            dependencies {
                consumer project(":")
            }
        """
    }

    @Language("JAVA")
    private String defineCustomInterpreter() {
        return """
            class TestVariantMatchingFailureInterpreter implements VariantMatchingFailureInterpreter {
                @Override
                java.util.Optional<String> process(String producerDisplayName, org.gradle.api.attributes.HasAttributes requested, List<? extends org.gradle.api.attributes.HasAttributes> candidates) {
                    return java.util.Optional.of("Test matcher always matches!")
                }
            }
        """
    }
}
