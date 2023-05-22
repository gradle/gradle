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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.util.SetSystemProperties
import org.junit.Rule

/**
 * Tests for [org.gradle.api.artifacts.ArtifactView ArtifactView] that ensure it uses the "live" attributes
 * of the configuration that created it to select files and artifacts.
 *
 * These tests assume a situation where a consumer should resolve the secondary "classes" variant of a producer
 * and obtain the "main" directory as the only file.  As the necessary attributes are not present on the
 * consumer's compileClasspath configuration until the beforeLocking callback method is called, the previous
 * incorrect behavior of the ArtifactView was to select the standard jar file variant instead of the "classes"
 * variant.
 */
class ArtifactViewAttributesIntegrationTest extends AbstractIntegrationSpec {
    @Rule SetSystemProperties systemProperties

    private static declareIncomingFiles = "def incomingFiles = configurations.compileClasspath.incoming.files"
    private static declareIncomingArtifacts = "def incomingArtifacts = configurations.compileClasspath.incoming.artifacts"
    private static declareArtifactViewFiles = "def artifactViewFiles = configurations.compileClasspath.incoming.artifactView {  }.files"
    private static declareArtifactViewArtifacts = "def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView {  }.artifacts"

    private static iterateIncomingFiles = """println 'Incoming Files:'
incomingFiles.each {
    println 'Name: ' + it.name
}
println ''
"""
    private static iterateIncomingArtifacts = """println 'Incoming Artifacts:'
incomingArtifacts.each {
    println 'Name: ' + it.id.name + ', File: ' + it.id.file.name
}
println ''
"""
    private static iterateArtifactViewFiles = """println 'Artifact View Files:'
artifactViewFiles.each {
    println 'Name: ' + it.name
}
println ''
"""
    private static iterateArtifactViewArtifacts = """println 'Artifact View Artifacts:'
artifactViewArtifacts.each {
    println 'Name: ' + it.id.name + ', File: ' + it.id.file.name
}
println ''
"""

    private static filesComparison = "assert incomingFiles*.name == artifactViewFiles*.name"
    private static artifactComparison = "assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name"

    def setup() {
        settingsFile << """
            rootProject.name = "consumer"
            include "producer"
        """

        file("producer/build.gradle").text = """
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
        """

        // Restore static property to default value prior to each test
        //noinspection GroovyAccessibility
        DefaultConfiguration.useLegacyAttributeSnapshottingBehavior = null
    }

    def "declare and iterate incoming files, then declare and iterate artifact view files"() {
        buildFile << """
            $declareIncomingFiles
            $iterateIncomingFiles

            $declareArtifactViewFiles
            $iterateArtifactViewFiles

            $filesComparison
        """

        expect:
        run ':help'
    }

    def "declare and iterate artifact view files, then declare and iterate incoming files"() {
        buildFile << """
            $declareArtifactViewFiles
            $iterateArtifactViewFiles

            $declareIncomingFiles
            $iterateIncomingFiles

            $filesComparison
        """

        expect:
        run ':help'
    }

    def "declare incoming files, then declare artifact view files, then iterate both"() {
        buildFile << """
            $declareIncomingFiles
            $declareArtifactViewFiles

            $iterateIncomingFiles
            $iterateArtifactViewFiles

            $filesComparison
        """

        expect:
        run ':help'
    }

    def "declare and iterate incoming artifacts, then declare and iterate artifact view artifacts"() {
        buildFile << """
            $declareIncomingArtifacts
            $iterateIncomingArtifacts

            $declareArtifactViewArtifacts
            $iterateArtifactViewArtifacts

            $artifactComparison
        """

        expect:
        run ':help'
    }

    def "declare and iterate artifact view artifacts, then declare and iterate incoming artifacts"() {
        buildFile << """
            $declareArtifactViewArtifacts
            $iterateArtifactViewArtifacts

            $declareIncomingArtifacts
            $iterateIncomingArtifacts

            $artifactComparison
        """

        expect:
        run ':help'
    }

    def "declare incoming artifacts, then declare artifact view artifacts, then iterate both"() {
        buildFile << """
            $declareIncomingArtifacts
            $declareArtifactViewArtifacts

            $iterateIncomingArtifacts
            $iterateArtifactViewArtifacts

            $artifactComparison
        """

        expect:
        run ':help'
    }

    def "declare artifacts (incoming, then artifact view) and files (incoming, then artifact view) , then iterate artifacts (incoming, then artifact view), then files (incoming, then artifact view) "() {
        buildFile << """
            $declareIncomingArtifacts
            $declareArtifactViewArtifacts
            $declareIncomingFiles
            $declareArtifactViewFiles

            $iterateIncomingArtifacts
            $iterateArtifactViewArtifacts
            $iterateIncomingFiles
            $iterateArtifactViewFiles

            $filesComparison
            $artifactComparison
        """

        expect:
        run ':help'
    }

    def "declare artifacts (artifact view, then incoming) and files (artifact view, then incoming) , then iterate artifacts (incoming, then artifact view), then files (incoming, then artifact view) "() {
        buildFile << """
            $declareArtifactViewArtifacts
            $declareIncomingArtifacts
            $declareArtifactViewFiles
            $declareIncomingFiles

            $iterateIncomingArtifacts
            $iterateArtifactViewArtifacts
            $iterateIncomingFiles
            $iterateArtifactViewFiles

            $filesComparison
            $artifactComparison
        """

        expect:
        run ':help'
    }

    def "declare artifacts (incoming, then artifact view) and files (incoming, then artifact view) , then iterate artifacts (artifact view then incoming), then files (artifact view, then incoming) "() {
        buildFile << """
            $declareIncomingArtifacts
            $declareArtifactViewArtifacts
            $declareIncomingFiles
            $declareArtifactViewFiles

            $iterateArtifactViewArtifacts
            $iterateIncomingArtifacts
            $iterateArtifactViewFiles
            $iterateIncomingFiles

            $filesComparison
            $artifactComparison
        """

        expect:
        run ':help'
    }

    def "declare artifacts (artifact view, then incoming) and files (artifact view, then incoming) , then iterate artifacts (artifact view then incoming), then files (artifact view, then incoming)"() {
        buildFile << """
            $declareArtifactViewArtifacts
            $declareIncomingArtifacts
            $declareArtifactViewFiles
            $declareIncomingFiles

            $iterateArtifactViewArtifacts
            $iterateIncomingArtifacts
            $iterateArtifactViewFiles
            $iterateIncomingFiles

            $filesComparison
            $artifactComparison
        """

        expect:
        run ':help'
    }

    def "declare and iterate artifact view files, then artifact view artifacts, then incoming files, then incoming artifacts"() {
        buildFile << """
            $declareArtifactViewFiles
            $iterateArtifactViewFiles

            $declareArtifactViewArtifacts
            $iterateArtifactViewArtifacts

            $declareIncomingFiles
            $iterateIncomingFiles

            $declareIncomingArtifacts
            $iterateIncomingArtifacts

            $filesComparison
            $artifactComparison
        """

        expect:
        run ':help'
    }


    def "declare and iterate incoming files, then incoming artifacts, then artifact view files, then artifact view artifacts"() {
        buildFile << """
            $declareIncomingFiles
            $iterateIncomingFiles

            $declareIncomingArtifacts
            $iterateIncomingArtifacts

            $declareArtifactViewFiles
            $iterateArtifactViewFiles

            $declareArtifactViewArtifacts
            $iterateArtifactViewArtifacts

            $filesComparison
            $artifactComparison
        """

        expect:
        run ':help'
    }

    @ToBeFixedForConfigurationCache(because = "Legacy behavior is not supported with the configuration cache")
    def "use legacy behavior to declare and iterate artifact view files, then declare and iterate incoming files"() {
        System.setProperty(DefaultConfiguration.USE_LEGACY_ATTRIBUTE_SNAPSHOT_BEHAVIOR, Boolean.TRUE.toString())

        buildFile << """
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

    @ToBeFixedForConfigurationCache(because = "Legacy behavior is not supported with the configuration cache")
    def "use legacy behavior to declare and iterate incoming artifacts, then declare and iterate artifact view artifacts"() {
        System.setProperty(DefaultConfiguration.USE_LEGACY_ATTRIBUTE_SNAPSHOT_BEHAVIOR, Boolean.TRUE.toString())

        buildFile << """
            $declareIncomingArtifacts
            $iterateIncomingArtifacts

            $declareArtifactViewArtifacts
            $iterateArtifactViewArtifacts

            // The legacy behavior was for the incoming artifacts variant selection to not use the lazy attributes
            assert incomingArtifacts*.id.file.name != artifactViewArtifacts*.id.file.name
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The org.gradle.configuration.use-legacy-attribute-snapshot-behavior system property has been deprecated. This is scheduled to be removed in Gradle 9.0. Please remove this flag and use the current default behavior. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#legacy_attribute_snapshotting")
        run ':help'
    }

    def "test adding an attribute lately without using beforeLocking or the java plugin still produces same files"() {
        file("producer/build.gradle").text = """
            def attr = Attribute.of("test-attr", String)

            configurations {
                producerConf1 {
                    attributes.attribute(attr, 'foo')
                    outgoing.artifact file('file1')
                }
                producerConf2 {
                    attributes.attribute(attr, 'bar')
                    outgoing.artifact file('file2')
                }
            }
        """

        buildFile.text = """
            def attr = Attribute.of("test-attr", String)

            configurations {
                filesConsumerConf {
                    attributes.attribute(attr, 'foo')
                }

                artifactsConsumerConf {
                    attributes.attribute(attr, 'foo')
                }
            }

            dependencies {
                filesConsumerConf(project(":producer"))
                artifactsConsumerConf(project(":producer"))
            }

            tasks.register("verifySameFiles") {
                def incomingFiles = configurations.filesConsumerConf.incoming.files
                def artifactViewFiles = configurations.filesConsumerConf.incoming.artifactView {}.files
                configurations.filesConsumerConf.attributes.attribute(attr, 'bar')

                doLast {
                    assert incomingFiles*.name == ['file2']
                    assert artifactViewFiles*.name == ['file2']
                }
            }

            tasks.register("verifySameArtifacts") {
                def incomingArtifacts = configurations.artifactsConsumerConf.incoming.artifacts
                def artifactViewArtifacts = configurations.artifactsConsumerConf.incoming.artifactView {}.artifacts
                configurations.artifactsConsumerConf.attributes.attribute(attr, 'bar')

                doLast {
                    assert incomingArtifacts*.id.file.name == ['file2']
                    assert artifactViewArtifacts*.id.file.name == ['file2']
                }
            }
        """

        expect:
        run ':verifySameFiles'

        and:
        run ':verifySameArtifacts'
    }

    @ToBeFixedForConfigurationCache(because = "Legacy behavior is not supported with the configuration cache")
    def "test legacy behavior when adding an attribute lately without using beforeLocking or the java plugin still produces same files"() {
        System.setProperty(DefaultConfiguration.USE_LEGACY_ATTRIBUTE_SNAPSHOT_BEHAVIOR, Boolean.TRUE.toString())

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

    @ToBeFixedForConfigurationCache(because = "Legacy behavior is not supported with the configuration cache")
    def "test legacy behavior when adding an attribute lately without using beforeLocking or the java plugin still produces same artifacts"() {
        System.setProperty(DefaultConfiguration.USE_LEGACY_ATTRIBUTE_SNAPSHOT_BEHAVIOR, Boolean.TRUE.toString())

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
