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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

/**
 * Tests for [org.gradle.api.artifacts.ArtifactView ArtifactView] that ensure it uses the "live" attributes
 * of the configuration that created it to select files and artifacts.
 *
 * As the necessary attributes are not present on the consumer's compileClasspath configuration until the
 * beforeLocking callback method is called, the previous incorrect behavior of the ArtifactView was to select without
 * considering these attributes.  This has been corrected to use a "live" view
 * of the attributes, to ensure that late changes to the attributes are reflected in the selection.
 *
 * These tests do not use any specific plugin (such as `java-library`) and manually make late changes to the attributes
 * used to resolve the files that are then verified.
 */
class ArtifactViewAttributesIntegrationTest extends AbstractArtifactViewAttributesIntegrationTest {
    @Override
    List<String> getExpectedFileNames() {
        return ["chocolate.jar"]
    }

    @Override
    List<String> getExpectedAttributes() {
        return ['flavor = chocolate']
    }

    @Override
    String getTestedClasspathName() {
        return "consumerConf"
    }

    def setup() {
        file("producer/build.gradle") << """
            interface Flavor extends Named {}
            def flavor = Attribute.of(Flavor)

            configurations {
                producerConfVanilla {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'vanilla'))
                    }
                    outgoing {
                        artifact file('vanilla.jar')
                    }
                }
                producerConfChocolate {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'chocolate'))
                    }
                    outgoing {
                        artifact file('chocolate.jar')
                    }
                }
            }
        """

        buildFile << """
            interface Flavor extends Named {}
            def flavor = Attribute.of(Flavor)

            configurations {
                consumerConf {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'chocolate'))
                    }
                }
            }

            dependencies {
                consumerConf(project(":producer"))
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "Need to create an ArtifactView from the incoming ResolvableDependencies at execution time")
    def "test adding an attribute lately using beforeLocking without produces same files"() {
        buildFile << """
            configurations.${testedClasspathName}.beforeLocking {
                attributes.attribute(flavor, objects.named(Flavor, 'vanilla'))
            }

            tasks.register('verifyFiles', FilesVerificationTask) {
                incoming = configurations.${testedClasspathName}.incoming
            }
        """

        expect:
        run ':verifyFiles'
        output.contains("Resolved file: vanilla.jar") // Late attributes have changed the expectations
        output.contains("Attribute: flavor = vanilla")
    }

    @ToBeFixedForConfigurationCache(because = "Need to create an ArtifactView from the incoming ResolvableDependencies at execution time")
    def "test adding an attribute lately without using beforeLocking still produces same files"() {
        buildFile << """
            tasks.register('verifyFiles', FilesVerificationTask) {
                incoming = configurations.${testedClasspathName}.incoming
                afterCreatingArtifactView = { configurations.${testedClasspathName}.attributes.attribute(flavor, objects.named(Flavor, 'vanilla')) }
            }
        """

        expect:
        run ':verifyFiles'
        output.contains("Resolved file: vanilla.jar") // Late attributes have changed the expectations
        output.contains("Attribute: flavor = vanilla")
    }

    @ToBeFixedForConfigurationCache(because = "Need to create an ArtifactView from the incoming ResolvableDependencies at execution time")
    def "test adding an attribute lately using beforeLocking without produces same artifacts"() {
        buildFile << """
            configurations.${testedClasspathName}.beforeLocking {
                attributes.attribute(flavor, objects.named(Flavor, 'vanilla'))
            }

            tasks.register('verifyArtifacts', ArtifactsVerificationTask) {
                incoming = configurations.${testedClasspathName}.incoming
            }
        """

        expect:
        run ':verifyArtifacts'
        output.contains("Resolved file: vanilla.jar") // Late attributes have changed the expectations
        output.contains("Attribute: flavor = vanilla")
    }

    @ToBeFixedForConfigurationCache(because = "Need to create an ArtifactView from the incoming ResolvableDependencies at execution time")
    def "test adding an attribute lately without using beforeLocking still produces same artifacts"() {
        buildFile << """
            tasks.register('verifyArtifacts', ArtifactsVerificationTask) {
                incoming = configurations.${testedClasspathName}.incoming
                afterCreatingArtifactView = { configurations.${testedClasspathName}.attributes.attribute(flavor, objects.named(Flavor, 'vanilla')) }
            }
        """

        expect:
        run ':verifyArtifacts'
        output.contains("Resolved file: vanilla.jar") // Late attributes have changed the expectations
        output.contains("Attribute: flavor = vanilla")
    }
}
