/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r212
import groovy.transform.NotYetImplemented
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject

class SmokeCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    def "throws IllegalStateException when no participants are added"() {
        when:
        createComposite()
        then:
        thrown(IllegalStateException)
    }

    // TODO: Remove this test
    def "throws UnsupportedOperationException when trying to retrieve anything other than EclipseProject"() {
        when:
        withCompositeConnection(projectDir("project")) { connection ->
            connection.getModels(IdeaProject)
        }
        then:
        thrown(UnsupportedOperationException)
    }

    def "throws IllegalArgumentException when trying to add overlapping participants"() {
        given:
        def project = projectDir("project")
        def overlapping = project.file("overlapping").createDir()
        when:
        withCompositeConnection([ project, overlapping ]) { connection ->
            connection.getModels(EclipseProject)
        }
        then:
        thrown(IllegalArgumentException)
    }

    def "throws IllegalArgumentException when trying to retrieve a non-model type"() {
        when:
        withCompositeConnection(projectDir("project")) { connection ->
            connection.getModels(Object)
        }
        then:
        thrown(IllegalArgumentException)
    }

    def "throws IllegalStateException when using a closed connection"() {
        given:
        def project = populate("project") {
            settingsFile << "rootProject.name = 'project'"
        }
        when:
        withCompositeConnection(project) { connection ->
            def models = connection.getModels(EclipseProject)
            connection.close()
            def modelsAgain = connection.getModels(EclipseProject)
        }
        then:
        thrown(IllegalStateException)
    }

    def "propagates errors when trying to retrieve models"() {
        given:
        def project = populate("project") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
            buildFile << "throw new RuntimeException()"
        }
        when:
        withCompositeConnection(project) { connection ->
            def models = connection.getModels(EclipseProject)
            connection.close()
        }
        then:
        def e = thrown(BuildException)
        e.getMessage().contains("Could not fetch model of type 'EclipseProject'")
        e.getCause().getCause().getCause() instanceof RuntimeException
    }

    // TODO: Figure out how to make ProjectConnection.close fail
    @NotYetImplemented
    def "propagates errors when trying to close a connection"() {
        given:
        def project = populate("project") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
        }
        when:
        withCompositeConnection(project) { connection ->
            def models = connection.getModels(EclipseProject)
            connection.close()
        }
        then:
        thrown(RuntimeException)
    }

    def "fails to retrieve model when participant is not a Gradle project"() {
        when:
        withCompositeConnection(projectDir("project-does-not-exist")) { connection ->
            connection.getModels(EclipseProject)
        }
        then:
        def e = thrown(BuildException)
        e.getMessage().contains("Could not fetch model of type 'EclipseProject'")
        def underlyingCause = e.getCause().getCause()
        underlyingCause.getMessage().contains("project-does-not-exist' does not exist")
    }
}
