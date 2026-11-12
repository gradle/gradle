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
import org.gradle.integtests.fixtures.ArtifactlessPrimaryVariantFixture
import spock.lang.Issue

/**
 * Verifies artifact selection from a configuration whose primary variant has no artifacts but which declares secondary variants.
 * <p>
 * Such a primary variant is selected when it is the best match. Only the requested attributes decide whether it is a better match
 * than a variant with artifacts, and a transformation of a variant with artifacts is preferred over it unless it matches every requested
 * attribute. Prior to Gradle 10, selecting it requires the {@code CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview. Without it,
 * the primary variant is not eligible for selection, and a deprecation warning is emitted when it would be selected.
 */
@Issue("https://github.com/gradle/gradle/issues/29630")
final class VariantsWithoutArtifactsWithSecondaryVariantsIntegrationTest extends AbstractIntegrationSpec implements ArtifactlessPrimaryVariantFixture {

    def "primary variant without artifacts and without secondary variants is selected (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)

        expect:
        succeeds("resolve")
        assertResolved([])

        where:
        previewEnabled << [false, true]
    }

    def "primary variant with an explicit artifact is selected (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true", "-PregisterExplicitArtifact=true")
        assertResolved([file("my-explicit-primary-artifact.jar")])

        where:
        previewEnabled << [false, true]
    }

    def "primary variant with lazily added artifacts is selected (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterLazyPrimaryArtifacts=my-lazy-primary-artifact.jar")
        assertResolved([file("my-lazy-primary-artifact.jar")])

        where:
        previewEnabled << [false, true]
    }

    def "compatible secondary variant with artifacts is selected over primary variant without artifacts (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)

        expect:
        // The secondary variant has an artifactType attribute that the request does not ask for, which does not make it a worse match
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true", "-PsecondaryValue=value1")
        assertResolved([file("my-secondary-artifact.jar")])

        where:
        previewEnabled << [false, true]
    }

    def "equally compatible secondary variant without artifacts is selected over primary variant without artifacts (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PsecondaryValue=value1")
        assertResolved([])

        where:
        previewEnabled << [false, true]
    }

    def "transformation of a secondary variant is selected over primary variant without artifacts that lacks a requested attribute (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)
        registerJarToTxtTransform()

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true", "-PsecondaryValue=value1")
        outputContains("my-secondary-artifact.jar.txt]")

        where:
        previewEnabled << [false, true]
    }

    def "primary variant without artifacts is ignored when it is a better match for the requested attributes and emits a deprecation warning"() {
        given:
        registerValue1CompatibilityRule()
        expectArtifactlessPrimaryVariantDeprecation(PRODUCER, PRIMARY_VARIANT)

        expect:
        // The primary variant has the requested value, the secondary variant only a compatible one
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true", "-PsecondaryValue=value1-compatible")
        assertResolved([file("my-secondary-artifact.jar")])
    }

    def "primary variant without artifacts is selected when it is a better match for the requested attributes"() {
        given:
        registerValue1CompatibilityRule()
        considerArtifactlessPrimaryVariants()

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true", "-PsecondaryValue=value1-compatible")
        assertResolved([])
    }

    def "primary variant without artifacts that is incompatible with the request is not selected (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)
        buildKotlinFile << """
            tasks.register("resolveView") {
                inputs.files(resolvableConfiguration.get().incoming.artifactView {
                    attributes {
                        attribute(myAttribute, "value2")
                    }
                }.files)
                doFirst {
                    println("Resolved: " + inputs.files.files)
                }
            }
        """

        expect:
        succeeds("resolveView", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
        assertResolved([file("my-secondary-artifact.jar")])

        where:
        previewEnabled << [false, true]
    }

    def "ambiguity among secondary variants does not report primary variant without artifacts (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)
        addOtherSecondaryVariant()

        expect:
        fails("resolve", "-PregisterSecondaryVariant=true", "-PsecondaryValue=value1")
        failure.assertHasCause("The consumer was configured to find attribute 'myAttribute' with value 'value1'. However we cannot choose between the following variants of root project 'test'")
        failure.assertHasErrorOutput("Configuration ':consumableConfiguration' variant 'myOtherSecondaryVariant'")
        failure.assertNotOutput("Configuration ':consumableConfiguration' declares")

        where:
        previewEnabled << [false, true]
    }

    def "primary variant without artifacts that declares every requested attribute is ignored instead of a transformation and emits a deprecation warning"() {
        given:
        registerMarkingTransform("myAttribute", "value2", "value1")
        expectArtifactlessPrimaryVariantDeprecation(PRODUCER, PRIMARY_VARIANT)

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
        outputContains("my-secondary-artifact.jar.transformed]")
    }

    def "primary variant without artifacts that declares every requested attribute is selected instead of a transformation"() {
        given:
        registerMarkingTransform("myAttribute", "value2", "value1")
        considerArtifactlessPrimaryVariants()

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
        assertResolved([])
    }

    def "primary variant without artifacts is ignored when only a transformation of it matches and emits a deprecation warning"() {
        given:
        requestStageThatOnlyPrimaryVariantDeclares()
        registerMarkingTransform("stage", "raw", "processed")
        expectArtifactlessPrimaryVariantDeprecation(PRODUCER, PRIMARY_VARIANT)

        expect:
        fails("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
        failure.assertHasCause("No variants of root project 'test' match the consumer attributes")
        failure.assertHasErrorOutput("Configuration ':consumableConfiguration' variant 'mySecondaryVariant'")
        failure.assertNotOutput("Configuration ':consumableConfiguration' declares")
    }

    def "transformation of primary variant without artifacts is selected when nothing else matches"() {
        given:
        requestStageThatOnlyPrimaryVariantDeclares()
        registerMarkingTransform("stage", "raw", "processed")
        considerArtifactlessPrimaryVariants()

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
        assertResolved([])
    }

    def "primary variant without artifacts is ignored when it is a better match than tied secondary variants and emits a deprecation warning"() {
        given:
        registerValue1CompatibilityRule()
        addOtherSecondaryVariant("value1-compatible")
        expectArtifactlessPrimaryVariantDeprecation(PRODUCER, PRIMARY_VARIANT)

        expect:
        fails("resolve", "-PregisterSecondaryVariant=true", "-PsecondaryValue=value1-compatible")
        failure.assertHasCause("The consumer was configured to find attribute 'myAttribute' with value 'value1'. However we cannot choose between the following variants of root project 'test'")
        failure.assertHasErrorOutput("Configuration ':consumableConfiguration' variant 'myOtherSecondaryVariant'")
        failure.assertNotOutput("Configuration ':consumableConfiguration' declares")
    }

    def "primary variant without artifacts is selected when it is a better match than tied secondary variants"() {
        given:
        registerValue1CompatibilityRule()
        addOtherSecondaryVariant("value1-compatible")
        considerArtifactlessPrimaryVariants()

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PsecondaryValue=value1-compatible")
        assertResolved([])
    }

    def "no matching variant failure lists primary variant without artifacts only when it is eligible (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)
        requestStageThatOnlyPrimaryVariantDeclares()

        expect:
        fails("resolve", "-PregisterSecondaryVariant=true", "-PregisterSecondaryArtifact=true")
        failure.assertHasCause("No variants of root project 'test' match the consumer attributes")
        failure.assertHasErrorOutput("Configuration ':consumableConfiguration' variant 'mySecondaryVariant'")
        failure.error.contains("Configuration ':consumableConfiguration' declares") == previewEnabled

        where:
        previewEnabled << [false, true]
    }

    def "primary variant without artifacts is ignored when no other variant matches and emits a deprecation warning"() {
        given:
        expectArtifactlessPrimaryVariantDeprecation(PRODUCER, PRIMARY_VARIANT)

        expect:
        fails("resolve", "-PregisterSecondaryVariant=true")
        failure.assertHasCause("No variants of root project 'test' match the consumer attributes")
        failure.assertHasErrorOutput("Configuration ':consumableConfiguration' variant 'mySecondaryVariant'")
        failure.assertNotOutput("Configuration ':consumableConfiguration' declares")
    }

    def "primary variant without artifacts is selected when no other variant matches"() {
        given:
        considerArtifactlessPrimaryVariants()

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true")
        assertResolved([])
    }

    def "empty lazy artifact collection on primary variant counts as no artifacts"() {
        given:
        expectArtifactlessPrimaryVariantDeprecation(PRODUCER, PRIMARY_VARIANT)

        expect:
        fails("resolve", "-PregisterSecondaryVariant=true", "-PregisterLazyPrimaryArtifacts=")
        failure.assertHasCause("No variants of root project 'test' match the consumer attributes")
    }

    def "lenient artifact view resolves to no artifacts without a deprecation warning when only primary variant without artifacts matches (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)
        buildKotlinFile << """
            tasks.register("resolveView") {
                inputs.files(resolvableConfiguration.get().incoming.artifactView {
                    attributes {
                        attribute(myAttribute, "value1")
                    }
                }.files)
                doFirst {
                    println("Resolved: " + inputs.files.files)
                }
            }
        """

        expect:
        succeeds("resolveView", "-PregisterSecondaryVariant=true")
        assertResolved([])

        where:
        previewEnabled << [false, true]
    }

    def "primary variant without artifacts is an available variant only when considered (preview enabled: #previewEnabled)"() {
        given:
        considerArtifactlessPrimaryVariants(previewEnabled)
        buildKotlinFile << """
            configurations.consumable("otherConfiguration") {
                attributes {
                    attribute(myAttribute, "value3")
                }
                outgoing.artifact(project.file("other.jar"))
            }
            configurations.resolvable("otherResolvableConfiguration") {
                extendsFrom(dependencyScope.get())
                attributes {
                    attribute(myAttribute, "value3")
                }
            }
        """

        when:
        succeeds("dependencyInsight", "--configuration", "otherResolvableConfiguration", "--dependency", ":", "--all-variants", "-PregisterSecondaryVariant=true")

        then:
        outputContains("Variant otherConfiguration:")
        outputContains("Variant consumableConfiguration-mySecondaryVariant:")
        output.contains("Variant consumableConfiguration:") == previewEnabled

        where:
        previewEnabled << [false, true]
    }

    private static final String PRODUCER = "root project 'test'"
    private static final String PRIMARY_VARIANT = "configuration ':consumableConfiguration'"

    def setup() {
        settingsFile << "rootProject.name = 'test'\n"
        file("my-secondary-artifact.jar").text = "secondary"
        buildKotlinFile << """
            val myAttribute = Attribute.of("myAttribute", String::class.java)

            val consumableConfiguration = configurations.consumable("consumableConfiguration") {
                attributes {
                    attribute(myAttribute, "value1")
                }

                providers.gradleProperty("registerLazyPrimaryArtifacts").orNull?.let { names ->
                    outgoing.artifacts(provider { names.split(",").filter { it.isNotEmpty() }.map { project.file(it) } })
                }

                if (providers.gradleProperty("registerSecondaryVariant").orNull == "true") {
                    outgoing.variants.create("mySecondaryVariant") {
                        attributes {
                            attribute(myAttribute, providers.gradleProperty("secondaryValue").getOrElse("value2"))
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

    private void registerValue1CompatibilityRule() {
        buildKotlinFile << """
            abstract class Value1Compatible : AttributeCompatibilityRule<String> {
                override fun execute(details: CompatibilityCheckDetails<String>) {
                    if (details.consumerValue == "value1" && details.producerValue == "value1-compatible") {
                        details.compatible()
                    }
                }
            }

            dependencies {
                attributesSchema.attribute(myAttribute).compatibilityRules.add(Value1Compatible::class.java)
            }
        """
    }

    private void addOtherSecondaryVariant(String value = "value1") {
        buildKotlinFile << """
            consumableConfiguration.get().outgoing.variants.create("myOtherSecondaryVariant") {
                attributes {
                    attribute(myAttribute, "${value}")
                }
            }
        """
    }

    /**
     * Requests a {@code stage} attribute that only the primary variant declares, with a different value.
     */
    private void requestStageThatOnlyPrimaryVariantDeclares() {
        buildKotlinFile << """
            val stage = Attribute.of("stage", String::class.java)
            dependencies {
                attributesSchema.attribute(stage)
            }
            consumableConfiguration.get().outgoing.attributes {
                attribute(stage, "raw")
            }
            resolvableConfiguration.get().attributes {
                attribute(stage, "processed")
            }
        """
    }

    private void registerMarkingTransform(String attribute, String from, String to) {
        buildKotlinFile << """
            abstract class MarkTransformed : TransformAction<TransformParameters.None> {
                @get:InputArtifact
                abstract val input: Provider<FileSystemLocation>

                override fun transform(outputs: TransformOutputs) {
                    val inputFile = input.get().asFile
                    inputFile.copyTo(outputs.file(inputFile.name + ".transformed"))
                }
            }

            dependencies {
                registerTransform(MarkTransformed::class) {
                    from.attribute(${attribute}, "${from}")
                    to.attribute(${attribute}, "${to}")
                }
            }
        """
    }

    private void registerJarToTxtTransform() {
        buildKotlinFile << """
            abstract class AppendTxt : TransformAction<TransformParameters.None> {
                @get:InputArtifact
                abstract val input: Provider<FileSystemLocation>

                override fun transform(outputs: TransformOutputs) {
                    val inputFile = input.get().asFile
                    inputFile.copyTo(outputs.file(inputFile.name + ".txt"))
                }
            }

            dependencies {
                registerTransform(AppendTxt::class) {
                    from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                    to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "txt")
                }
            }

            resolvableConfiguration.get().attributes {
                attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "txt")
            }
        """
    }

    private void assertResolved(List<File> artifacts) {
        outputContains("Resolved: " + artifacts*.path.toString())
    }
}
