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

    def "can instantiate component instances in Groovy DSL"() {
        given:
        buildFile << """
            ${factoryRegistrationGroovy()}

            components {
                thing(JvmSoftwareComponentInternal)
                comp(JvmSoftwareComponentInternal)
            }

            task verify {
                assert components.thing instanceof DefaultJvmSoftwareComponent
                assert components.comp instanceof DefaultJvmSoftwareComponent
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate component instances in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            ${factoryRegistrationKotlin()}

            components {
                create<JvmSoftwareComponentInternal>("thing")
                create<JvmSoftwareComponentInternal>("comp")
            }

            tasks.register("verify") {
                assert(components.named("thing").get() is DefaultJvmSoftwareComponent)
                assert(components.named("comp").get() is DefaultJvmSoftwareComponent)
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
                assert components.comp instanceof DefaultJvmSoftwareComponent
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
                assert(components.named("comp").get() is DefaultJvmSoftwareComponent)
            }
        """

        expect:
        succeeds "verify"
    }

    def "can configure java component added by java-library plugin in Groovy DSL"() {
        given:
        buildFile << """
            plugins {
                id('java-library')
            }

            ${importStatements()}

            components {
                java {
                    withJavadocJar()
                }
            }

            task verify {
                assert components.java instanceof DefaultJvmSoftwareComponent
                assert configurations.javadocElements
                assert sourceSets.main
            }
        """

        expect:
        succeeds "verify"
    }

    // TODO: Eventually we want accessors to be generated for the `java` component so that
    // we can use `java {}` instead of `named<JvmSoftwareComponentInternal>("java") {}`.
    def "can configure java component added by java-library plugin in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            plugins {
                id("java-library")
            }

            ${importStatements()}

            components {
                named<JvmSoftwareComponentInternal>("java") {
                    withJavadocJar()
                }
            }

            tasks.register("verify") {
                assert(components.named("java").get() is DefaultJvmSoftwareComponent)
                assert(configurations.named("javadocElements").isPresent())
                assert(sourceSets.named("main").isPresent())
            }
        """

        expect:
        succeeds "verify"
    }

    def "can not registerFeature with multiple component instances"() {
        given:
        buildKotlinFile << """
            plugins {
                id("java-base")
            }

            ${factoryRegistrationKotlin()}

            sourceSets {
                val custom by registering
            }

            components {
                create<JvmSoftwareComponentInternal>("module")
                create<JvmSoftwareComponentInternal>("thing")
            }

            java {
                registerFeature("myFeature") {
                    usingSourceSet(sourceSets["custom"])
                }
            }
        """

        expect:
        fails("tasks")
        failure.assertHasErrorOutput("Cannot register feature because multiple JVM components are present. The following components were found: module, thing")
    }

    private static final String factoryRegistrationGroovy() {
        """
            ${importStatements()}

            components {
                registerBinding(JvmSoftwareComponentInternal, DefaultJvmSoftwareComponent)
            }
        """
    }

    private static final String factoryRegistrationKotlin() {
        """
            ${importStatements()}

            components {
                registerBinding(JvmSoftwareComponentInternal::class.java, DefaultJvmSoftwareComponent::class.java)
            }
        """
    }

    /**
     * Since JvmSoftwareComponent has no non-internal type, we always need to import it.
     */
    private static final String importStatements() {
        """
            import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
            import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent
        """
    }
}
