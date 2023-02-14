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

package org.gradle.jvm.component.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests {@link DefaultJvmSoftwareComponent}.
 */
class DefaultJvmSoftwareComponentIntegrationTest extends AbstractIntegrationSpec {

    def "cannot instantiate component instances without the java base plugin applied"() {
        given:
        buildFile << """
            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponentInternal)
            }
        """

        expect:
        fails "help"
        result.assertHasErrorOutput("The java-base plugin must be applied in order to create instances of DefaultJvmSoftwareComponent.")
    }

    def "can instantiate component instances with java-base plugin applied in Groovy DSL"() {
        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistrationGroovy()}

            components {
                thing(JvmSoftwareComponentInternal)
                feature(JvmSoftwareComponentInternal)
            }

            task verify {
                assert components.thing instanceof DefaultJvmSoftwareComponent
                assert sourceSets.thing

                assert components.feature instanceof DefaultJvmSoftwareComponent
                assert sourceSets.feature
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate component instances with java-base plugin applied in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            plugins {
                id("java-base")
            }

            ${factoryRegistrationKotlin()}

            components {
                create<JvmSoftwareComponentInternal>("thing")
                create<JvmSoftwareComponentInternal>("feature")
            }

            tasks.register("verify") {
                assert(components.named("thing").get() is DefaultJvmSoftwareComponent)
                assert(sourceSets.named("thing").isPresent())

                assert(components.named("feature").get() is DefaultJvmSoftwareComponent)
                assert(sourceSets.named("feature").isPresent())
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate component instances adjacent to java component with java-library plugin applied in Groovy DSL"() {
        given:
        buildFile << """
            plugins {
                id('java-library')
            }

            ${factoryRegistrationGroovy()}

            components {
                comp(JvmSoftwareComponentInternal)
            }

            task verify {
                assert components.java instanceof DefaultJvmSoftwareComponent
                assert sourceSets.main

                assert components.comp instanceof DefaultJvmSoftwareComponent
                assert sourceSets.comp
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate component instances adjacent to java component with java-library plugin applied in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            ${factoryRegistrationKotlin()}

            components {
                create<JvmSoftwareComponentInternal>("comp")
            }

            tasks.register("verify") {
                assert(components.named("java").get() is DefaultJvmSoftwareComponent)
                assert(sourceSets.named("main").isPresent())

                assert(components.named("comp").get() is DefaultJvmSoftwareComponent)
                assert(sourceSets.named("comp").isPresent())
            }
        """

        expect:
        succeeds "verify"
    }

    private static final String factoryRegistrationGroovy() {
        """
            import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
            import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent

            components {
                registerFactory(JvmSoftwareComponentInternal) {
                    objects.newInstance(DefaultJvmSoftwareComponent, it, it)
                }
            }
        """
    }

    private static final String factoryRegistrationKotlin() {
        """
            import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
            import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent

            components {
                registerFactory(JvmSoftwareComponentInternal::class.java) {
                    objects.newInstance(DefaultJvmSoftwareComponent::class.java, it, it)
                }
            }
        """
    }
}
