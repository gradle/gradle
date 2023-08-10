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
import org.gradle.test.fixtures.dsl.GradleDsl

import static org.gradle.test.fixtures.dsl.GradleDsl.*

class VariantMatchingFailureIntepreterIntegrationTest extends AbstractIntegrationSpec {
    def "can register a failure interpreter using #dsl"() {
        (dsl == GROOVY ? buildFile : buildKotlinFile) << """
            ${defineCustomInterpreter(dsl)}
            ${registerCustomListener(dsl)}

            ${setupAmbiguousVariantSelectionFailure(dsl)}

            ${forceConsumerResolution(dsl)}
        """

        expect:
        fails "help", "outgoingVariants", "resolvableConfigurations"
        errorOutput.contains("Could not resolve all files for configuration ':consumer'.")

        where:
        dsl << [GROOVY, KOTLIN]
    }

    def "default JDK mismatch failure interpreter is automatically added by plugin using #dsl"() {
        (dsl == GROOVY ? buildFile : buildKotlinFile) << """
            plugins {
                id("java-base")
            }

            ${setupAmbiguousVariantSelectionFailure(dsl)}

            ${forceConsumerResolution(dsl)}
        """

        expect:
        fails "help", "outgoingVariants", "resolvableConfigurations"
        errorOutput.contains("Could not resolve all files for configuration ':consumer'.")

        where:
        dsl << [GROOVY, KOTLIN]
    }

    def "can register a failure interpreter with the java library plugin applied"() {
        (dsl == GROOVY ? buildFile : buildKotlinFile) << """
            plugins {
                id("java-base")
            }

            ${defineCustomInterpreter(dsl)}
            ${registerCustomListener(dsl)}

            ${setupAmbiguousVariantSelectionFailure(dsl)}

            ${forceConsumerResolution(dsl)}
        """

        expect:
        fails "help", "outgoingVariants", "resolvableConfigurations"
        errorOutput.contains("Could not resolve all files for configuration ':consumer'.")

        where:
        dsl << [GROOVY, KOTLIN]
    }

    def "can NOT register a failure interpreter via a test suite"() {
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${defineCustomInterpreter()}
            ${setupAmbiguousVariantSelectionFailure()}

            testing {
                suites {
                    mySuite(JvmTestSuite) {
                        dependencies.attributesSchema.registerVariantSelectionListener(new TestVariantSelectionListener())
                    }
                }
            }

            configurations.consumer.incoming.files.each { println it }
        """

        expect:
        fails "help"
        errorOutput.contains("Could not get unknown property 'attributesSchema' for object of type org.gradle.api.plugins.jvm.internal.DefaultJvmComponentDependencies.")
    }

    private String setupAmbiguousVariantSelectionFailure(GradleDsl dsl = GROOVY) {
        if (dsl == GROOVY) {
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
        } else {
            return """
                val color = Attribute.of("color", String::class.java)
                val shape = Attribute.of("shape", String::class.java)

                configurations {
                    register("producer1") {
                        isCanBeConsumed = true
                        attributes.attribute(color, "blue")
                        attributes.attribute(shape, "round")
                        outgoing.artifact(file("a1.jar"))
                    }
                    register("producer2") {
                        isCanBeConsumed = true
                        attributes.attribute(color, "blue")
                        attributes.attribute(shape, "square")
                        outgoing.artifact(file("a2.jar"))
                    }
                    register("consumer") {
                        isCanBeConsumed = false
                        isCanBeResolved = true
                        attributes.attribute(color, "blue")

                        // Ensure that the variants added by the `java-library` plugin do not match and aren't considered in Step 1 of variant matching
                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.DOCUMENTATION))
                    }
                }

                dependencies {
                    add("consumer", project(":"))
                }
            """
        }
    }

    private String defineCustomInterpreter(GradleDsl dsl = GROOVY) {
        if (dsl == GROOVY) {
            return """
                class TestVariantSelectionListener implements VariantSelectionListener {
                    @Override
                    java.util.Optional<String> onFailure(String producerDisplayName, org.gradle.api.attributes.HasAttributes requested, List<? extends org.gradle.api.attributes.HasAttributes> candidates) {
                        return java.util.Optional.of("Test matcher always matches!")
                    }
                }
            """
        } else {
            return """
                typealias OptionalString = java.util.Optional<String>
                class TestVariantSelectionListener : VariantSelectionListener {
                    override fun onFailure(producerDisplayName: String, requested: org.gradle.api.attributes.HasAttributes, candidates: List<org.gradle.api.attributes.HasAttributes>): OptionalString {
                        return OptionalString.of("Test matcher always matches!")
                    }
                }
            """
        }
    }

    private String registerCustomListener(GradleDsl dsl = GROOVY) {
        if (dsl == GROOVY) {
            return """
                dependencies.attributesSchema.registerVariantSelectionListener(new TestVariantSelectionListener())
            """
        } else {
            return """
                dependencies.attributesSchema.registerVariantSelectionListener(TestVariantSelectionListener())
            """
        }
    }

    private String forceConsumerResolution(GradleDsl dsl = GROOVY) {
        if (dsl == GROOVY) {
            return """
                configurations.consumer.incoming.files.each { println it }
            """
        } else {
            return """
                configurations.getByName("consumer").incoming.files.forEach { println(it) }
            """
        }
    }
}
