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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl

/**
 * Tests that resolution failures caused by {@link org.gradle.api.artifacts.ArtifactView ArtifactView}
 * rather than configurations indicate this in the error message.
 */
class ArtifactViewResolutionErrorIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = "consumer"
            include "producer"
        """

        file("producer/build.gradle.kts") << """
            val flavor: Attribute<String> = Attribute.of("flavor", String::class.java)

            configurations {
                register("producerConfVanilla") {
                    attributes.attribute(flavor, "vanilla")
                    outgoing.artifact(file("vanilla.jar"))
                }
                register("producerConfChocolate") {
                    attributes.attribute(flavor, "chocolate")
                    outgoing.artifact(file("chocolate.jar"))
                }
            }
        """

        buildKotlinFile << """
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val flavor: Attribute<String> = Attribute.of("flavor", String::class.java)

            configurations.register("consumerConf")

            dependencies {
                configurations["consumerConf"](project(":producer"))
            }
        """
    }

    def "successful artifact view - no change to attributes"() {
        buildKotlinFile << """
            ${addGoodAttributesToConf()}

            tasks.register("verify") {
                val artifactViewFiles = ${defaultArtifactView()}.files

                doLast {
                    assert(artifactViewFiles.map { it.name } == listOf("vanilla.jar"))
                }
            }
        """

        expect:
        succeeds("verify")
    }

    def "successful artifact view - change to attributes"() {
        buildKotlinFile << """
            ${addGoodAttributesToConf()}

            tasks.register("verify") {
                val artifactViewFiles = ${artifactViewWithGoodAttributes("custom", "chocolate")}.files

                doLast {
                    assert(artifactViewFiles.map { it.name } == listOf("chocolate.jar"))
                }
            }
        """

        expect:
        succeeds("verify")
    }

    def "artifact view files resolution error with default name"() {
        buildKotlinFile << """
            tasks.register("verify") {
                val artifactViewFiles = ${artifactViewWithBadAttributes()}.files

                doLast {
                    assert(artifactViewFiles.map { it.name } == listOf("vanilla.jar"))
                }
            }
        """

        expect:
        fails("verify")
        failureCauseContains("Could not resolve all files for artifact view for configuration ':consumerConf'.")
        !errorOutput.contains("Could not resolve all files for configuration ':consumerConf'.")
    }

    def "artifact view files resolution error with custom name"() {
        buildKotlinFile << """
            tasks.register("verify") {
                val artifactViewFiles = ${artifactViewWithBadAttributes("custom")}.files

                doLast {
                    assert(artifactViewFiles.map { it.name } == listOf("vanilla.jar"))
                }
            }
        """

        expect:
        fails("verify")
        failureCauseContains("Could not resolve all files for artifact view: 'custom' for configuration ':consumerConf'.")
        !errorOutput.contains("Could not resolve all files for configuration ':consumerConf'.")
    }

    def "artifact view artifacts resolution error with default name"() {
        buildKotlinFile << """
            tasks.register("verify") {
                val artifactViewArtifacts = ${artifactViewWithBadAttributes()}.artifacts

                doLast {
                    artifactViewArtifacts.artifacts.forEach { println(it.file.name) }
                    assert(artifactViewArtifacts.artifacts.map { it.file.name } == listOf("vanilla.jar"))
                }
            }
        """

        expect:
        fails("verify")
        failureCauseContains("Could not resolve all artifacts for artifact view for configuration ':consumerConf'.")
        !errorOutput.contains("Could not resolve all artifacts for configuration ':consumerConf'.")
    }

    def "artifact view artifacts resolution error with custom name"() {
        buildKotlinFile << """
            tasks.register("verify") {
                val artifactViewArtifacts = ${artifactViewWithBadAttributes("custom")}.artifacts

                doLast {
                    artifactViewArtifacts.artifacts.forEach { println(it.file.name) }
                    assert(artifactViewArtifacts.artifacts.map { it.file.name } == listOf("vanilla.jar"))
                }
            }
        """

        expect:
        fails("verify")
        failureCauseContains("Could not resolve all artifacts for artifact view: 'custom' for configuration ':consumerConf'.")
        !errorOutput.contains("Could not resolve all artifacts for configuration ':consumerConf'.")
    }

    private String addGoodAttributesToConf() {
        return """
            configurations.named("consumerConf").configure {
                ${setSuccessfulFlavor()}
            }
        """
    }

    private String addBadAttributesToConf() {
        return """
            configurations.named("consumerConf").configure {
                ${setFailingFlavor()}
            }
        """
    }

    private String defaultArtifactView(String name = null) {
        return """configurations.named("consumerConf").get().incoming.artifactView {
                    ${setName(name)}
                    withVariantReselection()
                }"""
    }

    private String artifactViewWithGoodAttributes(String name = null, String flavor = 'vanilla') {
        return """configurations.named("consumerConf").get().incoming.artifactView {
                    ${setName(name)}
                    ${setSuccessfulFlavor(flavor)}
                    withVariantReselection()
                }"""
    }

    private String artifactViewWithBadAttributes(String name = null) {
        return """configurations.named("consumerConf").get().incoming.artifactView {
lenient(false)
                    ${setName(name)}
                    ${setFailingFlavor()}
                    withVariantReselection()
                }"""
    }

    private String setSuccessfulFlavor(String flavor = 'vanilla') {
        return setFlavor(flavor)
    }

    private String setFailingFlavor() {
        return setFlavor("cinnamon")
    }

    private String setFlavor(String name) {
        return """
                attributes {
                    attribute(flavor, "$name")
                }
        """
    }

    private String setName(String name) {
        return name != null ? 'displayName = "' + name + '"' : ''
    }
}
