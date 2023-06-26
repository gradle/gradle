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

/**
 * Tests for ArtifactView that ensure it uses the "live" attributes of the configuration that created it to select files and artifacts.
 *
 * As the necessary attributes are not present on the consumer's compileClasspath configuration until the
 * beforeLocking callback method is called, the previous incorrect behavior of the ArtifactView was to select without
 * considering these attributes.  This has been corrected to use a "live" view
 * of the attributes, to ensure that late changes to the attributes are reflected in the selection.
 *
 * These tests use the `java-library` plugin and should resolve the secondary "classes" variant of the producer
 * and obtain the "main" directory as the only file, instead of the standard jar file variant, which was the incorrect
 * behavior.
 */
class JavaLibraryResolutionAttributesOrderingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = "consumer"
            include "producer"
        """
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

            abstract class FilesVerificationTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getIncomingFiles()

                @TaskAction
                def verify() {
                    assert incomingFiles*.name == ["main"]
                }
            }
        """
    }

    def "resolve the main classes through incoming.files"() {
        buildFile << """
            task verifyFiles(type: FilesVerificationTask) {
                incomingFiles.from(configurations.compileClasspath.incoming.files)
            }
        """
        expect:
        succeeds ':verifyFiles'
    }
    def "resolve the main classes through incoming.artifacts"() {
        buildFile << """
            task verifyFiles(type: FilesVerificationTask) {
                incomingFiles.from(configurations.compileClasspath.incoming.artifacts.artifactFiles)
            }
        """
        expect:
        succeeds ':verifyFiles'
    }
    def "resolve the main classes through artifactView.files"() {
        buildFile << """
            task verifyFiles(type: FilesVerificationTask) {
                incomingFiles.from(configurations.compileClasspath.incoming.artifactView {}.files)
            }
        """
        expect:
        succeeds ':verifyFiles'
    }
    def "resolve the main classes through artifactView.artifact"() {
        buildFile << """
            task verifyFiles(type: FilesVerificationTask) {
                incomingFiles.from(configurations.compileClasspath.incoming.artifactView {}.artifacts.artifactFiles)
            }
        """
        expect:
        succeeds ':verifyFiles'
    }
}
