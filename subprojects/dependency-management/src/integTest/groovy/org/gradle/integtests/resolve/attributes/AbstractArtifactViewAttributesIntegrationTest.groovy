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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

/**
 * Base class for tests that verify the variant selection behavior of artifact views and artifact collections
 * with lately-set attributes on their originating configuration.
 *
 * These tests assume a situation where the root consumer project will resolve a variant of a nested producer project.
 */
abstract class AbstractArtifactViewAttributesIntegrationTest extends AbstractIntegrationSpec {
    abstract List<String> getExpectedFileNames()
    abstract List<String> getExpectedAttributes()
    abstract String getTestedClasspathName()

    def setup() {
        settingsFile << """
            rootProject.name = "consumer"
            include "producer"
        """

        file("producer/build.gradle").createFile()

        buildFile << """
            abstract class FilesVerificationTask extends DefaultTask {
                @Input
                ResolvableDependencies incoming

                @Input
                Action<FilesVerificationTask> beforeCreatingArtifactView = { }
                @Input
                Action<FilesVerificationTask> afterCreatingArtifactView = { }

                @TaskAction
                def verify() {
                    def incomingFiles = incoming.files
                    beforeCreatingArtifactView.execute(this)
                    def artifactView = incoming.artifactView { }
                    def artifactViewFiles = artifactView.files
                    afterCreatingArtifactView.execute(this)

                    assert incomingFiles*.name == artifactViewFiles*.name
                    incomingFiles.each {
                        println 'Resolved file: ' + it.name
                    }
                    artifactView.attributes.keySet().each { attribute ->
                        println 'Attribute: ' + attribute.name + ' = ' + artifactView.attributes.getAttribute(attribute)
                    }
                }
            }

            abstract class ArtifactsVerificationTask extends DefaultTask {
                @Input
                ResolvableDependencies incoming

                @Input
                Action<FilesVerificationTask> beforeCreatingArtifactView = { }
                @Input
                Action<FilesVerificationTask> afterCreatingArtifactView = { }

                @TaskAction
                def verify() {
                    def incomingArtifacts = incoming.artifacts
                    beforeCreatingArtifactView.execute(this)
                    def artifactView = incoming.artifactView { }
                    def artifactViewArtifacts = artifactView.artifacts
                    afterCreatingArtifactView.execute(this)

                    assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name
                    incomingArtifacts.each {
                        println 'Resolved file: ' + it.id.file.name
                    }
                    artifactView.attributes.keySet().each { attribute ->
                        println 'Attribute: ' + attribute.name + ' = ' + artifactView.attributes.getAttribute(attribute)
                    }
                }
            }
        """
    }

    @ToBeFixedForConfigurationCache(because = "Need to create an ArtifactView from the incoming ResolvableDependencies at execution time")
    def "resolve files after creating artifact view"() {
        buildFile << """
            tasks.register('verifyFiles', FilesVerificationTask) {
                incoming = configurations.${testedClasspathName}.incoming
            }
        """

        expect:
        run ':verifyFiles'
        expectedFileNames.each {assert output.contains("Resolved file: $it") }
        expectedAttributes.each { assert output.contains("Attribute: $it") }
    }

    @ToBeFixedForConfigurationCache(because = "Need to create an ArtifactView from the incoming ResolvableDependencies at execution time")
    def "resolve files before creating artifact view"() {
        buildFile << """
            tasks.register('verifyFiles', FilesVerificationTask) {
                incoming = configurations.${testedClasspathName}.incoming
                // Force resolution before creating the artifact view
                beforeCreatingArtifactView = { incoming.files.forEach { it.exists() } }
            }
        """

        expect:
        run ':verifyFiles'
        expectedFileNames.each {assert output.contains("Resolved file: $it") }
        expectedAttributes.each { assert output.contains("Attribute: $it") }
    }

    @ToBeFixedForConfigurationCache(because = "Need to create an ArtifactView from the incoming ResolvableDependencies at execution time")
    def "resolve artifacts after creating artifact view"() {
        buildFile << """
            tasks.register('verifyArtifacts', ArtifactsVerificationTask) {
                incoming = configurations.${testedClasspathName}.incoming
            }
        """

        expect:
        run ':verifyArtifacts'
        expectedFileNames.each {assert output.contains("Resolved file: $it") }
        expectedAttributes.each { assert output.contains("Attribute: $it") }
    }

    @ToBeFixedForConfigurationCache(because = "Need to create an ArtifactView from the incoming ResolvableDependencies at execution time")
    def "resolve artifacts before creating artifact view"() {
        buildFile << """
            tasks.register('verifyArtifacts', ArtifactsVerificationTask) {
                incoming = configurations.${testedClasspathName}.incoming
                // Force resolution before creating the artifact view
                beforeCreatingArtifactView = { incoming.artifacts.forEach { it.id.file.exists() } }
            }
        """

        expect:
        run ':verifyArtifacts'
        expectedFileNames.each {assert output.contains("Resolved file: $it") }
        expectedAttributes.each { assert output.contains("Attribute: $it") }
    }
}
