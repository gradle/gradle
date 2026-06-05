/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Verifies the auto-applied {@code org.gradle.fallback-variant} attribute and its default
 * schema disambiguation rule, which together ensure that when a primary variant has no
 * artifacts but secondary variants exist, the secondaries win during artifact selection
 * unless the consumer explicitly opts into the fallback primary.
 */
@Issue("https://github.com/gradle/gradle/issues/29630")
class FallbackVariantIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildKotlinFile << """
            val myAttr = Attribute.of("myAttr", String::class.java)
            val format = Attribute.of("format", String::class.java)

            dependencies {
                attributesSchema {
                    attribute(myAttr)
                    attribute(format)
                }
            }

            val producer = configurations.consumable("producer") {
                attributes {
                    attribute(myAttr, "wanted")
                }
                outgoing.variants.create("secondary") {
                    attributes {
                        attribute(myAttr, "wanted")
                        attribute(format, "jar")
                    }
                    artifact(layout.projectDirectory.file("secondary.jar"))
                }
            }

            val consumerDeps = configurations.dependencyScope("consumerDeps")
            val consumer = configurations.resolvable("consumer") {
                extendsFrom(consumerDeps.get())
                attributes {
                    attribute(myAttr, "wanted")
                    if (providers.gradleProperty("requestFormat").orNull == "true") {
                        attribute(format, "jar")
                    }
                    if (providers.gradleProperty("requestPrimary").orNull == "true") {
                        attribute(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE, project.objects.named(org.gradle.api.attributes.FallbackVariant::class.java, "true"))
                    }
                    if (providers.gradleProperty("requestSecondary").orNull == "true") {
                        attribute(FallbackVariant.FALLBACK_VARIANT_ATTRIBUTE, project.objects.named(org.gradle.api.attributes.FallbackVariant::class.java, "false"))
                    }
                }
            }

            dependencies {
                consumerDeps(project(":"))
            }

            tasks.register("resolve") {
                inputs.files(consumer)
                doFirst {
                    println("Resolved: " + inputs.files.files.map { it.name })
                }
            }
        """
    }

    def "under-specified consumer request selects the secondary over the empty primary"() {
        expect:
        succeeds("resolve")
        outputContains("Resolved: [secondary.jar]")
    }

    def "consumer request that specifies the secondary's discriminating attribute selects the secondary"() {
        expect:
        succeeds("resolve", "-PrequestSecondary=true")
        outputContains("Resolved: [secondary.jar]")
    }

    def "consumer can opt into the fallback primary by explicitly requesting fallback-variant=true"() {
        expect:
        succeeds("resolve", "-PrequestPrimary=true")
        outputContains("Resolved: []")
    }
}
