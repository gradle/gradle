/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.project

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.DocumentationRegistry
import spock.lang.Specification

/**
 * Unit tests for {@link UnknownProjectStateException}.
 */
class UnknownProjectStateExceptionTest extends Specification {
    def "message names the project and points at the missing input declaration"() {
        given:
        def identifier = Stub(ProjectComponentIdentifier) {
            getDisplayName() >> "project ':lib'"
        }

        when:
        def exception = new UnknownProjectStateException(identifier)

        then:
        exception.message == "Could not access project ':lib'. " +
            "No task declared this project as part of an input, so it was not scheduled. " +
            "Properly declare all task inputs (including the result of any dependency resolutions) to ensure this project is scheduled for execution."
    }

    def "exposes the project component identifier"() {
        given:
        def identifier = Stub(ProjectComponentIdentifier) {
            getBuildTreePath() >> ":lib"
        }

        when:
        def exception = new UnknownProjectStateException(identifier)

        then:
        exception.identifier.is(identifier)
    }

    def "is an IllegalArgumentException for source-level backward compatibility"() {
        given:
        def identifier = Stub(ProjectComponentIdentifier) {
            getBuildTreePath() >> ":lib"
        }

        expect:
        new UnknownProjectStateException(identifier) instanceof IllegalArgumentException
    }

    def "uses build tree path so composite-build paths are unambiguous"() {
        given:
        def identifier = Stub(ProjectComponentIdentifier) {
            getDisplayName() >> "project ':includedBuild:lib'"
        }

        when:
        def exception = new UnknownProjectStateException(identifier)

        then:
        exception.message.contains("Could not access project ':includedBuild:lib'.")
        exception.message.contains("No task declared this project as part of an input")
    }

    def "provides two actionable resolutions including the upgrading-guide URL"() {
        given:
        def identifier = Stub(ProjectComponentIdentifier) {
            getBuildTreePath() >> ":lib"
        }
        def expectedUrl = new DocumentationRegistry().getDocumentationFor("upgrading_version_9", "undeclared_artifact_transform_input")

        when:
        def exception = new UnknownProjectStateException(identifier)

        then:
        exception.resolutions == [
            "Declare the files or artifacts produced by the configuration using the transform as a task input to properly wire it into the execution plan.",
            "Consult the upgrading guide for further information: " + expectedUrl
        ]
    }
}
