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
    def "warns if attribute combinations are not unique"() {
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

                    outgoing {
                        // Force identical capabilities by overriding the default
                        capability('org.gradle:sample:1.0')
                    }
                }

                sample2 {
                    canBeResolved = false
                    canBeConsumed = true

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }

                    outgoing {
                        // Force identical capabilities by overriding the default
                        capability('org.gradle:sample:1.0')
                    }
                }
            }""".stripIndent()

        expect:
        executer.expectDocumentedDeprecationWarning("Consumable configurations with identical capabilities within a project must have unique attributes, but configuration ':sample2' and configuration ':sample1' contain identical attribute sets.")
        succeeds("outgoingVariants")
    }

    def "the default capability never triggers warning, even if there is a match"() {
        given:
        settingsFile << "rootProject.name = 'sample'"
        buildFile << """
            plugins {
                id 'java'
            }

            group = 'org.gradle'
            version = '1.0'

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

                    outgoing {
                        // Force identical capabilities as the default
                        capability('org.gradle:sample:1.0')
                    }
                }
            }""".stripIndent()

        expect:
        succeeds("outgoingVariants")
        outputContains("org.gradle:sample:1.0 (default capability)")
    }

    def "attribute combinations can be repeated if capabilities differ"() {
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

                    outgoing {
                        capability('org.gradle:sample:1.0')
                    }
                }

                sample2 {
                    canBeResolved = false
                    canBeConsumed = true

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }

                    outgoing {
                        capability('org.gradle:sample:2.0')
                    }
                }
            }""".stripIndent()

        expect:
        succeeds("outgoingVariants")
    }

    def "attribute and capability combinations can be repeated across projects without a warning"() {
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

                    outgoing {
                        // Force identical capabilities by overriding the default
                        capability('org.gradle:sample:1.0')
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

                    outgoing {
                        // Force identical capabilities by overriding the default
                        capability('org.gradle:sample:1.0')
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

