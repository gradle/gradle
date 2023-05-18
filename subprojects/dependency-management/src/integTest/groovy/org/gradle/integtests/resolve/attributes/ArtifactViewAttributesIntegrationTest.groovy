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
import spock.lang.Ignore

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
    private static declareIncomingFiles = "def incomingFiles = configurations.compileClasspath.incoming.files"
    private static declareIncomingArtifacts = "def incomingArtifacts = configurations.compileClasspath.incoming.artifacts"
    private static declareArtifactViewFiles = "def artifactViewFiles = configurations.compileClasspath.incoming.artifactView { }.files"
    private static declareArtifactViewArtifacts = "def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView { }.artifacts"

    private static iterateIncomingFiles = """logger.warn 'Incoming Files:'
incomingFiles.each {
    logger.warn 'Name: ' + it.name
}
logger.warn ''
"""
    private static iterateIncomingArtifacts = """logger.warn 'Incoming Artifacts:'
incomingArtifacts.each {
    logger.warn 'Name: ' + it.id.name + ', File: ' + it.id.file.name
}
logger.warn ''
"""
    private static iterateArtifactViewFiles = """logger.warn 'Artifact View Files:'
artifactViewFiles.each {
    logger.warn 'Name: ' + it.name
}
logger.warn ''
"""
    private static iterateArtifactViewArtifacts = """logger.warn 'Artifact View Artifacts:'
artifactViewArtifacts.each {
    logger.warn 'Name: ' + it.id.name + ', File: ' + it.id.file.name
}
logger.warn ''
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

    @Ignore("There are 10,000s of these, so it takes some time, but is the ultimate sanity check")
    def "test all valid permutations of declaration and iteration order of files and artifacts on incoming vs. artifact view"() {
        buildFile << """
            logger.warn '******************'
            logger.warn '***** Script *****'
            logger.warn '******************'
            logger.warn ''
            logger.warn '''$code'''
            logger.warn ''

            logger.warn '******************'
            logger.warn '***** Output *****'
            logger.warn '******************'
            logger.warn ''
            $code

            assert incomingFiles*.name == artifactViewFiles*.name
            assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name
        """

        expect:
        run ':help'

        where:
        code << [
            declareIncomingFiles,
            declareIncomingArtifacts,
            declareArtifactViewFiles,
            declareArtifactViewArtifacts,
            iterateIncomingFiles,
            iterateIncomingArtifacts,
            iterateArtifactViewFiles,
            iterateArtifactViewArtifacts
        ].permutations().findAll { declarationOccursPriorToRelevantIteration(it) }.collect { it.join('\n') }
    }

    private boolean declarationOccursPriorToRelevantIteration(List<String> code) {
        return code.indexOf(declareIncomingFiles) < code.indexOf(iterateIncomingFiles) &&
            code.indexOf(declareIncomingArtifacts) < code.indexOf(iterateIncomingArtifacts) &&
            code.indexOf(declareArtifactViewFiles) < code.indexOf(iterateArtifactViewFiles) &&
            code.indexOf(declareArtifactViewArtifacts) < code.indexOf(iterateArtifactViewArtifacts);
    }
}
