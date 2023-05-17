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
    private static declareIncomingFiles = "def incomingFiles = configurations.compileClasspath.incoming.files\n"
    private static declareIncomingArtifacts = "def incomingArtifacts = configurations.compileClasspath.incoming.artifacts\n"
    private static declareArtifactViewFiles = "def artifactViewFiles = configurations.compileClasspath.incoming.artifactView { }.files\n"
    private static declareArtifactViewArtifacts = "def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView { }.artifacts\n"
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

    def "incoming declarations first during configuration phase"() {
        buildFile << """
            def incomingFiles = configurations.compileClasspath.incoming.files
            def incomingArtifacts = configurations.compileClasspath.incoming.artifacts
            def artifactViewFiles = configurations.compileClasspath.incoming.artifactView { }.files
            def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView { }.artifacts

            assert incomingFiles*.name == artifactViewFiles*.name
            assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name
        """

        expect:
        run ':help'
    }

    def "incoming declarations first during configuration phase, iteration during execution phase"() {
        buildFile << """
            tasks.register('verifySameFilesAndArtifacts') {
                def incomingFiles = configurations.compileClasspath.incoming.files
                def incomingArtifacts = configurations.compileClasspath.incoming.artifacts
                def artifactViewFiles = configurations.compileClasspath.incoming.artifactView { }.files
                def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView { }.artifacts

                doLast {
                    assert incomingFiles*.name == artifactViewFiles*.name
                    assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name
                }
            }
        """

        expect:
        run ':verifySameFilesAndArtifacts'
    }

    def "incoming declarations first during execution phase"() {
        buildFile << """
            tasks.register('verifySameFilesAndArtifacts') {
                doLast {
                    def incomingFiles = configurations.compileClasspath.incoming.files
                    def incomingArtifacts = configurations.compileClasspath.incoming.artifacts
                    def artifactViewFiles = configurations.compileClasspath.incoming.artifactView { }.files
                    def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView { }.artifacts

                    assert incomingFiles*.name == artifactViewFiles*.name
                    assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name
                }
            }
        """

        expect:
        run ':verifySameFilesAndArtifacts'
    }

    def "artifact view declarations first"() {
        buildFile << """
            def artifactViewFiles = configurations.compileClasspath.incoming.artifactView { }.files
            def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView { }.artifacts
            def incomingFiles = configurations.compileClasspath.incoming.files
            def incomingArtifacts = configurations.compileClasspath.incoming.artifacts

            assert incomingFiles*.name == artifactViewFiles*.name
            assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name
        """

        expect:
        run ':help'
    }

    def "incoming declarations first, then iteration to force resolution"() {
        buildFile << """
            def incomingFiles = configurations.compileClasspath.incoming.files
            def incomingArtifacts = configurations.compileClasspath.incoming.artifacts

            incomingFiles.size()
            incomingArtifacts.size()

            def artifactViewFiles = configurations.compileClasspath.incoming.artifactView { }.files
            def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView { }.artifacts

            assert incomingFiles*.name == artifactViewFiles*.name
            assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name
        """

        expect:
        run ':help'
    }

    def "incoming artifacts first"() {
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

    def "artifactView artifacts first"() {
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

    @Ignore
    def "mixer"() {
        buildFile << """
            logger.warn '******************'
            logger.warn '***** Script *****'
            logger.warn '******************'
            logger.warn ''
            logger.warn '''$codeBlock'''
            logger.warn ''

            logger.warn '******************'
            logger.warn '***** Output *****'
            logger.warn '******************'
            logger.warn ''
            $codeBlock

            assert incomingFiles*.name == artifactViewFiles*.name
            assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name
        """

        expect:
        run ':help'

        where:
        codeBlock << [
            declareIncomingFiles,
            declareIncomingArtifacts,
            declareArtifactViewFiles,
            declareArtifactViewArtifacts,
            iterateIncomingFiles,
            iterateIncomingArtifacts,
            iterateArtifactViewFiles,
            iterateArtifactViewArtifacts
        ].permutations().findAll { isValid(it) }.collect { it.join('\n') }
    }

    private boolean isValid(List<String> code) {
        return code.indexOf(declareIncomingFiles) < code.indexOf(iterateIncomingFiles) &&
            code.indexOf(declareIncomingArtifacts) < code.indexOf(iterateIncomingArtifacts) &&
            code.indexOf(declareArtifactViewFiles) < code.indexOf(iterateArtifactViewFiles) &&
            code.indexOf(declareArtifactViewArtifacts) < code.indexOf(iterateArtifactViewArtifacts);
    }
}
