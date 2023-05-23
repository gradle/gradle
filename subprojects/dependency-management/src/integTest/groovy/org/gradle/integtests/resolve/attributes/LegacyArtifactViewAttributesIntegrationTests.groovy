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

import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration

/**
 * Tests for [org.gradle.api.artifacts.ArtifactView ArtifactView] that confirm the legacy "snapshotting"
 * behavior is still accessible via a system setting flag.
 *
 * When this flag is set, lately added attributes on the originating configuration are not used to select
 * the variant using the artifact view.
 */
class LegacyArtifactViewAttributesIntegrationTests extends AbstractArtifactViewAttributesIntegrationTest {
    def setup() {
        System.setProperty(DefaultConfiguration.USE_LEGACY_ATTRIBUTE_SNAPSHOT_BEHAVIOR, 'true')
    }

    def cleanup() {
        System.clearProperty(DefaultConfiguration.USE_LEGACY_ATTRIBUTE_SNAPSHOT_BEHAVIOR)
        //noinspection GroovyAccessibility
        DefaultConfiguration.useLegacyAttributeSnapshottingBehavior = null // reset cached property in loaded class
    }

    def "use legacy behavior to declare and iterate artifact view files, then declare and iterate incoming files"() {
        file("producer/build.gradle") << """
            plugins {
                id 'java-library'
            }
        """

        buildFile << """
            plugins {
                id 'java-library'
            }

            dependencies {
                api project(':producer')
            }

            $declareArtifactViewFiles
            $iterateArtifactViewFiles

            $declareIncomingFiles
            $iterateIncomingFiles

            // The legacy behavior was for the artifact view variant selection to not use the lazy attributes
            assert incomingFiles*.name != artifactViewFiles*.name
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The org.gradle.configuration.use-legacy-attribute-snapshot-behavior system property has been deprecated. This is scheduled to be removed in Gradle 9.0. Please remove this flag and use the current default behavior. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#legacy_attribute_snapshotting")
        run ':help'
    }

    def "use legacy behavior to declare and iterate incoming artifacts, then declare and iterate artifact view artifacts"() {
        file("producer/build.gradle") << """
            plugins {
                id 'java-library'
            }
        """

        buildFile << """
            plugins {
                id 'java-library'
            }

            dependencies {
                api project(':producer')
            }

            $declareIncomingArtifacts
            $iterateIncomingArtifacts

            $declareArtifactViewArtifacts
            $iterateArtifactViewArtifacts

            // The legacy behavior was for the incoming artifacts variant selection to not use the lazy attributes
            assert incomingArtifacts*.id.file.name != artifactViewArtifacts*.id.file.name
        """

        expect:
        2.times { executer.expectDocumentedDeprecationWarning("The org.gradle.configuration.use-legacy-attribute-snapshot-behavior system property has been deprecated. This is scheduled to be removed in Gradle 9.0. Please remove this flag and use the current default behavior. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#legacy_attribute_snapshotting") }
        run ':help'
    }

    def "test legacy behavior when adding an attribute lately without using beforeLocking or the java plugin still produces same files"() {
        file("producer/build.gradle").text = """
            interface Flavor extends Named {}
            def flavor = Attribute.of(Flavor)

            configurations {
                producerConf1 {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'vanilla'))
                    }
                    outgoing {
                        artifact file('file-vanilla')
                    }
                }
                producerConf2 {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'chocolate'))
                    }
                    outgoing {
                        artifact file('file-chocolate')
                    }
                }
            }
        """

        buildFile.text = """
            interface Flavor extends Named {}
            def flavor = Attribute.of(Flavor)

            configurations {
                filesConsumerConf {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'vanilla'))
                    }
                }
            }

            dependencies {
                filesConsumerConf(project(":producer"))
            }

            tasks.register("verifySameFiles") {
                def incomingFiles = configurations.filesConsumerConf.incoming.files
                def artifactViewFiles = configurations.filesConsumerConf.incoming.artifactView { lenient = true }.files // snapshot is taken here
                configurations.filesConsumerConf.attributes.attribute(flavor, objects.named(Flavor, 'chocolate')) // not used for artifact view

                doLast {
                    assert incomingFiles*.name == artifactViewFiles*.name // fails
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The org.gradle.configuration.use-legacy-attribute-snapshot-behavior system property has been deprecated. This is scheduled to be removed in Gradle 9.0. Please remove this flag and use the current default behavior. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#legacy_attribute_snapshotting")
        fails ':verifySameFiles'
    }

    def "test legacy behavior when adding an attribute lately without using beforeLocking or the java plugin still produces same artifacts"() {
        file("producer/build.gradle").text = """
            interface Flavor extends Named {}
            def flavor = Attribute.of(Flavor)

            configurations {
                producerConf1 {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'vanilla'))
                    }
                    outgoing {
                        artifact file('file-vanilla')
                    }
                }
                producerConf2 {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'chocolate'))
                    }
                    outgoing {
                        artifact file('file-chocolate')
                    }
                }
            }
        """

        buildFile.text = """
            interface Flavor extends Named {}
            def flavor = Attribute.of(Flavor)

            configurations {
                artifactsConsumerConf {
                    attributes {
                        attribute(flavor, objects.named(Flavor, 'vanilla'))
                    }
                }
            }

            dependencies {
                artifactsConsumerConf(project(":producer"))
            }

            tasks.register("downloadArtifacts") {
                def incomingArtifacts = configurations.artifactsConsumerConf.incoming.artifacts // attributes snapshot is taken here
                configurations.artifactsConsumerConf.attributes.attribute(flavor, objects.named(Flavor, 'chocolate')) // not used

                doLast {
                    assert incomingArtifacts*.id.file.name == ['file-chocolate'] // fails
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The org.gradle.configuration.use-legacy-attribute-snapshot-behavior system property has been deprecated. This is scheduled to be removed in Gradle 9.0. Please remove this flag and use the current default behavior. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#legacy_attribute_snapshotting")
        fails ':downloadArtifacts'
    }
}
