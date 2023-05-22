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

/**
 * Tests for [org.gradle.api.artifacts.ArtifactView ArtifactView] that ensure it uses the "live" attributes
 * of the configuration that created it to select files and artifacts.
 *
 * As the necessary attributes are not present on the consumer's compileClasspath configuration until the
 * beforeLocking callback method is called, the previous incorrect behavior of the ArtifactView was to select without
 * considering these attributes.  This has been corrected to use a "live" view
 * of the attributes, to ensure that late changes to the attributes are reflected in the selection.
 *
 * Most of these tests use the `java-library` plugin and should resolve the secondary "classes" variant of the producer
 * and obtain the "main" directory as the only file, instead of the standard jar file variant, which was the legacy
 * behavior.
 */
class ArtifactViewAttributesIntegrationTest extends AbstractArtifactViewAttributesIntegrationTest {
    def setup() {
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
        """
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


    def "test adding an attribute lately without using beforeLocking or the java plugin still produces same files"() {
        // Replace text to avoid adding Java plugin
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

        // Replace text to avoid adding Java plugin
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
}
