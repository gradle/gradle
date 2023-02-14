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

    def "can instantiate and configure single element with closure"() {
        given:
        buildFile << """
            ${customComponentWithName("DefaultComponent")}

            components {
                registerBinding(SoftwareComponent, DefaultComponent)
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
                assert components.first instanceof DefaultComponent
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate and configure single element with configure method"() {
        given:
        buildFile << """
            ${customComponentWithName("DefaultComponent")}

            components.configure {
                registerBinding(SoftwareComponent, DefaultComponent)
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
                assert components.first instanceof DefaultComponent
            }
        """

        expect:
        succeeds "verify"
    }

    def "can instantiate multiple components"() {
        given:
        buildFile << """
            ${customComponentWithName("DefaultComponent")}
            ${customComponentWithName("CustomComponent")}

            components {
                registerBinding(SoftwareComponent, DefaultComponent)
                registerBinding(CustomComponent, CustomComponent)
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
                assert components.first instanceof DefaultComponent

                assert components.second.name == "second"
                assert components.second.value.get() == 2
                assert components.second instanceof DefaultComponent

                assert components.third.name == "third"
                assert components.third.value.get() == 3
                assert components.third instanceof CustomComponent
            }
        """

        expect:
        succeeds "verify"
    }

    def "can configure multiple elements"() {
        given:
        buildFile << """
            ${customComponentWithName("DefaultComponent")}

            components {
                registerBinding(SoftwareComponent, DefaultComponent)
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
                assert components.comp1 instanceof DefaultComponent

                assert components.comp2.name == "comp2"
                assert components.comp2.value.get() == 20
                assert components.comp2 instanceof DefaultComponent
            }
        """

        expect:
        succeeds "verify"
    }

    private static String customComponentWithName(String name) {
        """
            abstract class ${name} implements SoftwareComponent {

                private final String name

                @Inject
                public ${name}(String name) {
                    this.name = name
                }

                public String getName() {
                    return name
                }

                public abstract Property<Integer> getValue()
            }
        """
    }
}
