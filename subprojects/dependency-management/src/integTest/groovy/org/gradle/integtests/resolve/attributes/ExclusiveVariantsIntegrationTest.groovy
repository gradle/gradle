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
    def "matching attribute combinations and capability #capability fails to resolve"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                sample1 {
                    canBeResolved = false
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                    if (!"${capability}".equals('default')) {
                        outgoing.capability('org.test:sample:1.0')
                    }
                }

                sample2 {
                    canBeResolved = false
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                    if (!"${capability}".equals('default')) {
                        outgoing.capability('org.test:sample:1.0')
                    }
                }

                resolver {
                    assert canBeResolved
                    canBeConsumed = false
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }
            }

            dependencies {
                resolver(project)
            }

            tasks.register('resolveSample', Copy) {
                from configurations.resolver
                into layout.buildDirectory.dir('sampleContent')
            }
            """.stripIndent()

        expect:
        fails("resolveSample")
        failure.assertHasDocumentedCause("Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes, but configuration ':sample1' and [configuration ':sample2'] contain identical attribute sets. " +
            "Consider adding an additional attribute to one of the configurations to disambiguate them.  " +
            "Run the 'outgoingVariants' task for more details. ${documentationRegistry.getDocumentationRecommendationFor("information", "upgrading_version_7", "unique_attribute_sets")}")

        where:
        capability << ['default', 'org.test:sample:1.0']
    }

    def "matching attribute combinations and capability #capability triggers a warning in outgoingVariants report"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                sample1 {
                    canBeResolved = false
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                    if (!"${capability}".equals('default')) {
                        outgoing.capability('org.test:sample:1.0')
                    }
                }

                sample2 {
                    canBeResolved = false
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                    if (!"${capability}".equals('default')) {
                        outgoing.capability('org.test:sample:1.0')
                    }
                }
            }""".stripIndent()

        expect:
        succeeds("outgoingVariants")
        outputContains("Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes, but configuration ':sample2' and [configuration ':sample1'] contain identical attribute sets. Consider adding an additional attribute to one of the configurations to disambiguate them.")

        where:
        capability << ['default', 'org.test:sample:1.0']
    }

    def "matching attribute combinations, where one uses the default capability and one uses a matching explicit capability, triggers a warning"() {
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
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }

                sample2 {
                    canBeResolved = false
                    assert canBeConsumed

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

        succeeds("outgoingVariants")
        expect:
        outputContains("Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes, but configuration ':sample1' and [configuration ':sample2'] contain identical attribute sets.")
        outputContains("org.gradle:sample:1.0 (default capability)")
    }

    def "attribute combinations can be repeated if capabilities differ without a warning"() {
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
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }

                sample2 {
                    canBeResolved = false
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }

                    outgoing {
                        capability('org.gradle:test:2.0')
                    }
                }
            }""".stripIndent()

        expect:
        succeeds("outgoingVariants")
        outputDoesNotContain('Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes')
    }

    def "attribute combinations can be repeated if capabilities differ, including the default capability without a warning"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                sample1 {
                    canBeResolved = false
                    assert canBeConsumed

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
                    assert canBeConsumed

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
        outputDoesNotContain('Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes')
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
                    assert canBeConsumed

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
                    assert canBeConsumed

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
        outputDoesNotContain('Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes')
    }

    def "attribute and capability combinations, including the default capability, can be repeated across projects without a warning"() {
        given:
        def subADir = createDir("subA")
        subADir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            configurations {
                sampleA {
                    canBeResolved = false
                    assert canBeConsumed

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

        def subBDir = createDir("sample")
        subBDir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            group = 'org.gradle'
            version = '1.0'

            configurations {
                sampleB {
                    canBeResolved = false
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }
            }""".stripIndent()

        settingsFile << """
            include ':subA'
            include ':sample'
            """.stripIndent()

        buildFile << """
            tasks.register('allOutgoingVariants') {
                dependsOn ':subA:outgoingVariants', ':sample:outgoingVariants'
            }
            """.stripIndent()

        expect:
        succeeds("allOutgoingVariants")
        outputDoesNotContain('Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes')
    }

    def "attribute combinations and capabilities on resolvable configurations can match without a warning"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                sample1 {
                    assert canBeResolved

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }

                sample2 {
                    assert canBeResolved

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }
            }""".stripIndent()

        expect:
        succeeds("outgoingVariants")
        outputDoesNotContain('Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes')
    }

    def "attribute combinations and capabilities on legacy resolvable configurations can match without a warning"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations {
                sample1 {
                    // Configurations that are both resolvable and consumable are legacy and should not be used
                    assert canBeResolved
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }

                sample2 {
                    // Configurations that are both resolvable and consumable are legacy and should not be used
                    assert canBeResolved
                    assert canBeConsumed

                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "custom"))
                    }
                }
            }""".stripIndent()

        expect:
        succeeds("outgoingVariants")
        outputDoesNotContain('Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes')
    }
}

