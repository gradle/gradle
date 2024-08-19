/*
 * Copyright 2024 the original author or authors.
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
 * These tests are to verify the behavior when adding a secondary variant to a main variant
 * without artifacts.
 */
@Issue("https://github.com/gradle/gradle/issues/29630")
final class VariantsWithoutArtifactsWithSecondaryVariantsIntegrationTest extends AbstractIntegrationSpec {
    // region No Secondary Variants
    def "no secondary variants works fine for resolution"() {
        expect:
        succeeds("resolve")
        assertResolved([])
    }

    def "no secondary variants works fine for dependencyInsight"() {
        expect:
        succeeds("dependencyInsight", "--configuration", "resolvableConfiguration", "--dependency", ":")
    }

    def "no secondary variants - outgoingVariants report"() {
        expect:
        succeeds("outgoingVariants")
    }

    def "no secondary variants - resolvableConfigurations report"() {
        expect:
        succeeds("resolvableConfigurations")
    }
    // endregion No Secondary Variants

    // region Secondary Variant with No Artifacts
    def "adding secondary variant with no artifact works fine"() {
        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true")
        assertResolved([])
    }

    // Does this not failing indicate the report is unreliable?
    def "adding secondary variant with no artifact works fine for dependencyInsight"() {
        expect:
        succeeds("dependencyInsight", "--configuration", "resolvableConfiguration", "--dependency", ":")
    }

    def "adding secondary variant with no artifact - outgoingVariants report"() {
        expect:
        succeeds("outgoingVariants", "-PregisterSecondaryVariant=true")
    }

    def "adding secondary variant with no artifact - resolvableConfigurations report"() {
        expect:
        succeeds("resolvableConfigurations", "-PregisterSecondaryVariant=true")
    }
    // endregion Secondary Variant with No Artifacts

    // region Secondary Variant with no Artifacts with an Artifact explicitly added to main variant
    def "adding secondary variant with artifact explicitly added to main variant works fine for resolution"() {
        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterExplicitArtifact=true")
        assertResolved([file("my-explicit-primary-artifact.jar")])
    }

    def "adding secondary variant with artifact explicitly added to main variant works fine for dependencyInsight"() {
        expect:
        succeeds("dependencyInsight", "--configuration", "resolvableConfiguration", "--dependency", ":", "-PregisterSecondaryVariant=true", "-PregisterExplicitArtifact=true")
    }

    def "adding secondary variant with artifact explicitly added to main variant - outgoingVariants report"() {
        expect:
        succeeds("outgoingVariants", "-PregisterSecondaryVariant=true", "-PregisterExplicitArtifact=true")
    }

    def "adding secondary variant with artifact explicitly added to main variant - resolvableConfigurations report"() {
        expect:
        succeeds("resolvableConfigurations", "-PregisterSecondaryVariant=true", "-PregisterExplicitArtifact=true")
    }
    // endregion Secondary Variant with no Artifacts with an Artifact explicitly added to main variant

    // region Secondary Variant with an Artifact
    def "adding secondary variant with an artifact works fine"() {
        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
        assertResolved([])
    }

    def "adding secondary variant with an artifact works fine for dependencyInsight"() {
        expect:
        succeeds("dependencyInsight", "--configuration", "resolvableConfiguration", "--dependency", ":", "-PregisterSecondaryArtifact=true")
    }

    def "adding secondary variant with no artifact - outgoingVariants report"() {
        expect:
        succeeds("outgoingVariants", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
    }

    def "adding secondary variant with no artifact - resolvableConfigurations report"() {
        expect:
        succeeds("resolvableConfigurations", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
    }
    // endregion Secondary Variant with an Artifact

    // region Secondary Variant with an Artifact and an Artifact explicitly added to main variant
    def "adding secondary variant with an artifact with artifact explicitly added to main variant works fine for resolution"() {
        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true", "-PregisterExplicitArtifact=true")
        assertResolved([file("my-explicit-primary-artifact.jar")])
    }

    def "adding secondary variant with an artifact with artifact explicitly added to main variant works fine for dependencyInsight"() {
        expect:
        succeeds("dependencyInsight", "--configuration", "resolvableConfiguration", "--dependency", ":", "-PregisterSecondaryArtifact=true", "-PregisterExplicitArtifact=true")
    }

    def "adding secondary variant with no artifact with artifact explicitly added to main variant  - outgoingVariants report"() {
        expect:
        succeeds("outgoingVariants", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true", "-PregisterExplicitArtifact=true")
    }

    def "adding secondary variant with no artifact with artifact explicitly added to main variant  - resolvableConfigurations report"() {
        expect:
        succeeds("resolvableConfigurations", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true", "-PregisterExplicitArtifact=true")
    }
    // endregion Secondary Variant with an Artifact and an Artifact explicitly added to main variant

    def setup() {
        buildKotlinFile << """
            val myAttribute = Attribute.of("myAttribute", String::class.java)

            val consumableConfiguration = configurations.consumable("consumableConfiguration") {
                attributes {
                    attribute(myAttribute, "value1")
                }

                if (providers.gradleProperty("registerSecondaryVariant").orNull == "true") {
                    outgoing.variants.create("mySecondaryVariant") {
                        attributes {
                            attribute(myAttribute, "value2")
                        }

                        if (providers.gradleProperty("registerSecondaryArtifact").orNull == "true") {
                            artifact(project.file("my-secondary-artifact.jar"))
                        }
                    }
                }
            }

            if (providers.gradleProperty("registerExplicitArtifact").orNull == "true") {
                artifacts {
                    add(consumableConfiguration.name, project.file("my-explicit-primary-artifact.jar"))
                }
            }

            val dependencyScope = configurations.dependencyScope("dependencyScope")
            val resolvableConfiguration = configurations.resolvable("resolvableConfiguration") {
                extendsFrom(dependencyScope.get())
                attributes {
                    attribute(myAttribute, "value1")
                }
            }

            dependencies {
                attributesSchema.attribute(myAttribute)
                dependencyScope(project(":"))
            }

            tasks.register("resolve") {
                inputs.files(resolvableConfiguration)
                doFirst {
                    println("Resolved: " + inputs.files.files)
                }
            }
        """
    }

    private void assertResolved(List<File> artifacts) {
        outputContains("Resolved: " + artifacts*.path.toString())
    }
}
