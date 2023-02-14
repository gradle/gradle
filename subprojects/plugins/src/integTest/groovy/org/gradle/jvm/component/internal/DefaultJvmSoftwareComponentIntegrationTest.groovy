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
            ${factoryRegistration()}

            components {
                comp(JvmSoftwareComponentInternal)
            }
        """

        expect:
        fails "help"
        result.assertHasErrorOutput("The java-base plugin must be applied in order to create instances of DefaultJvmSoftwareComponent.")
    }

    def "can instantiate component instances with java-base plugin applied"() {
        given:
        buildFile << """
            plugins {
                id('java-base')
            }

            ${factoryRegistration()}

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

    def "can instantiate component instances adjacent to java component with java-library plugin applied"() {
        given:
        buildFile << """
            plugins {
                id('java-library')
            }

            ${factoryRegistration()}

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

    private static final String factoryRegistration() {
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
}
