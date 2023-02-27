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

package org.gradle.api.internal.component

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests {@link DefaultSoftwareComponentContainer}.
 */
class DefaultSoftwareComponentContainerIntegrationTest extends AbstractIntegrationSpec {

    def "can instantiate and configure single element with closure in Groovy DSL"() {
        given:
        buildFile << """
            ${customGroovyComponentWithName("TestComponent")}

            components {
                registerBinding(SoftwareComponent, DefaultTestComponent)
                first {
                    value = 1
                }
            }

            components {
                first {
                    value = 20
                }
            }

            task verify {
                assert components.first.name == "first"
                assert components.first.value.get() == 20
                assert components.first instanceof DefaultTestComponent
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate and configure single element with configure method in Groovy DSL"() {
        given:
        buildFile << """
            ${customGroovyComponentWithName("TestComponent")}

            components.configure {
                registerBinding(SoftwareComponent, DefaultTestComponent)
                first {
                    value = 1
                }
            }

            components.configure {
                first {
                    value = 20
                }
            }

            task verify {
                assert components.first.name == "first"
                assert components.first.value.get() == 20
                assert components.first instanceof TestComponent
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate and configure single element in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            ${customKotlinComponentWithName("TestComponent")}

            components {
                registerBinding(TestComponent::class.java, DefaultTestComponent::class.java)
                create<TestComponent>("first") {
                    value.set(1)
                }
            }

            components {
                named<TestComponent>("first") {
                    value.set(20)
                }
            }

            tasks.register("verify") {
                assert(components.named<TestComponent>("first").get().name == "first")
                assert(components.named<TestComponent>("first").get().value.get() == 20)
                assert(components.named<TestComponent>("first").get() is DefaultTestComponent)
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate multiple components in Groovy DSL"() {
        given:
        buildFile << """
            ${customGroovyComponentWithName("TestComponent")}
            ${customGroovyComponentWithName("CustomComponent")}

            components {
                registerBinding(SoftwareComponent, DefaultTestComponent)
                registerBinding(CustomComponent, DefaultCustomComponent)
                first {
                    value = 1
                }
                second(SoftwareComponent) {
                    value = 2
                }
                third(CustomComponent) {
                    value = 3
                }
            }

            task verify {
                assert components.first.name == "first"
                assert components.first.value.get() == 1
                assert components.first instanceof DefaultTestComponent

                assert components.second.name == "second"
                assert components.second.value.get() == 2
                assert components.second instanceof DefaultTestComponent

                assert components.third.name == "third"
                assert components.third.value.get() == 3
                assert components.third instanceof DefaultCustomComponent
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate multiple components in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            ${customKotlinComponentWithName("TestComponent")}
            ${customKotlinComponentWithName("CustomComponent")}

            components {
                registerBinding(TestComponent::class.java, DefaultTestComponent::class.java)
                registerBinding(CustomComponent::class.java, DefaultCustomComponent::class.java)
                create<TestComponent>("first") {
                    value.set(1)
                }
                create<CustomComponent>("second") {
                    value.set(2)
                }
            }

            tasks.register("verify") {
                assert(components.named<TestComponent>("first").get().name == "first")
                assert(components.named<TestComponent>("first").get().value.get() == 1)
                assert(components.named<TestComponent>("first").get() is DefaultTestComponent)

                assert(components.named<CustomComponent>("second").get().name == "second")
                assert(components.named<CustomComponent>("second").get().value.get() == 2)
                assert(components.named<CustomComponent>("second").get() is DefaultCustomComponent)
            }
        """

        expect:
        succeeds "verify"
    }

    def "can configure multiple elements in Groovy DSL"() {
        given:
        buildFile << """
            ${customGroovyComponentWithName("TestComponent")}

            components {
                registerBinding(SoftwareComponent, DefaultTestComponent)
                comp1 {
                    value = 1
                }
                comp2 {
                    value = 10
                }
            }

            components.comp1 {
                value = 2
            }

            components {
                comp2 {
                    value = 20
                }
            }

            task verify {
                assert components.comp1.name == "comp1"
                assert components.comp1.value.get() == 2
                assert components.comp1 instanceof DefaultTestComponent

                assert components.comp2.name == "comp2"
                assert components.comp2.value.get() == 20
                assert components.comp2 instanceof DefaultTestComponent
            }
        """

        expect:
        succeeds "verify"
    }

    def "can configure multiple elements in Kotlin DSL"() {
        given:
        buildKotlinFile << """
            ${customKotlinComponentWithName("TestComponent")}

            components {
                registerBinding(TestComponent::class.java, DefaultTestComponent::class.java)
                create<TestComponent>("comp1") {
                    value.set(1)
                }
                create<TestComponent>("comp2") {
                    value.set(10)
                }
            }

            components.named<TestComponent>("comp1") {
                value.set(2)
            }

            components {
                named<TestComponent>("comp2") {
                    value.set(20)
                }
            }

            tasks.register("verify") {
                assert(components.named<TestComponent>("comp1").get().name == "comp1")
                assert(components.named<TestComponent>("comp1").get().value.get() == 2)
                assert(components.named<TestComponent>("comp1").get() is DefaultTestComponent)

                assert(components.named<TestComponent>("comp2").get().name == "comp2")
                assert(components.named<TestComponent>("comp2").get().value.get() == 20)
                assert(components.named<TestComponent>("comp2").get() is DefaultTestComponent)
            }
        """

        expect:
        succeeds "verify"
    }

    private static String customGroovyComponentWithName(String name) {
        """
            interface ${name} extends SoftwareComponent {
                Property<Integer> getValue()
            }

            abstract class Default${name} implements ${name} {

                private final String name

                @Inject
                public Default${name}(String name) {
                    this.name = name
                }

                public String getName() {
                    return name
                }
            }
        """
    }

    private static String customKotlinComponentWithName(String name) {
        """
            interface ${name} : SoftwareComponent {
                val value: Property<Int>
            }
            abstract class Default${name} @Inject constructor(val name: String) : ${name} {
            }
        """
    }
}
