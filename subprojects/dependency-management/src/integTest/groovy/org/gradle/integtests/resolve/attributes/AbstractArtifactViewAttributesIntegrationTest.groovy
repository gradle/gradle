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
 * Base class for tests that verify the variant selection behavior of artifact views and artifact collections
 * with lately-set attributes on their originating configuration.
 *
 * These tests assume a situation where the root consumer project will resolve a variant of a nested producer project.
 */
abstract class AbstractArtifactViewAttributesIntegrationTest extends AbstractIntegrationSpec {
    protected static declareIncomingFiles = "def incomingFiles = configurations.compileClasspath.incoming.files"
    protected static declareIncomingArtifacts = "def incomingArtifacts = configurations.compileClasspath.incoming.artifacts"
    protected static declareArtifactViewFiles = "def artifactViewFiles = configurations.compileClasspath.incoming.artifactView {  }.files"
    protected static declareArtifactViewArtifacts = "def artifactViewArtifacts = configurations.compileClasspath.incoming.artifactView {  }.artifacts"

    protected static iterateIncomingFiles = """println 'Incoming Files:'
incomingFiles.each {
    println 'Name: ' + it.name
}
println ''
"""
    protected static iterateIncomingArtifacts = """println 'Incoming Artifacts:'
incomingArtifacts.each {
    println 'Name: ' + it.id.name + ', File: ' + it.id.file.name
}
println ''
"""
    protected static iterateArtifactViewFiles = """println 'Artifact View Files:'
artifactViewFiles.each {
    println 'Name: ' + it.name
}
println ''
"""
    protected static iterateArtifactViewArtifacts = """println 'Artifact View Artifacts:'
artifactViewArtifacts.each {
    println 'Name: ' + it.id.name + ', File: ' + it.id.file.name
}
println ''
"""

    protected static filesComparison = "assert incomingFiles*.name == artifactViewFiles*.name"
    protected static artifactComparison = "assert incomingArtifacts*.id.file.name == artifactViewArtifacts*.id.file.name"

    def setup() {
        settingsFile << """
            rootProject.name = "consumer"
            include "producer"
        """

        file("producer/build.gradle") << ''
    }
}
