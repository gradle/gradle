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

class ExclusiveVariantsIntegrationTest extends AbstractIntegrationSpec {
    def "attribute combinations are unique with projects"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                sample1 {
                    canBeResolved = false
                    canBeConsumed = true

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }

                sample2 {
                    canBeResolved = false
                    canBeConsumed = true

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }
            }""".stripIndent()

        expect:
        fails("outgoingVariants")
        result.assertHasErrorOutput("Consumable configurations within a project must have unique attribute sets, but configuration ':sample2' and configuration ':sample1' contain identical attribute sets.")
    }

    def "attribute combinations can be repeated across projects exclusive"() {
        given:
        def subADir = createDir("subA")
        subADir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            configurations {
                sampleA {
                    canBeResolved = false
                    canBeConsumed = true

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }
            }""".stripIndent()

        def subBDir = createDir("subB")
        subBDir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            configurations {
                sampleB {
                    canBeResolved = false
                    canBeConsumed = true

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }
            }""".stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

        buildFile << """
            tasks.register('allOutgoingVariants') {
                dependsOn ':subA:outgoingVariants', ':subB:outgoingVariants'
            }
            """.stripIndent()

        expect:
        succeeds("allOutgoingVariants")
    }
}

