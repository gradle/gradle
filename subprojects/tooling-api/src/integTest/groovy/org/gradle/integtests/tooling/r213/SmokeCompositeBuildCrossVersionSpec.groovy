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

package org.gradle.integtests.tooling.r213
import groovy.transform.NotYetImplemented
import org.gradle.integtests.tooling.fixture.GradleConnectionToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.eclipse.EclipseProject
/**
 * Basic tests for building and retrieving models from a composite.
 */
class SmokeCompositeBuildCrossVersionSpec extends GradleConnectionToolingApiSpecification {

    @NotYetImplemented
    def "throws IllegalArgumentException when trying to add overlapping participants"() {
        given:
        def project = projectDir("project")
        def overlapping = project.file("overlapping").createDir()

        when:
        getModelsWithGradleConnection(defineComposite(project, overlapping), EclipseProject)

        then:
        thrown(IllegalArgumentException)
    }

    def "throws IllegalArgumentException when trying to retrieve a non-model type"() {
        when:
        getModelsWithGradleConnection(projectDir.createDir('project'), Object)

        then:
        thrown(IllegalArgumentException)
    }

    def "throws IllegalStateException when using a closed connection"() {
        given:
        def project = populate("project") {
            settingsFile << "rootProject.name = 'project'"
        }
        when:
        withGradleConnection(project) { connection ->
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
        getUnwrappedModelsWithGradleConnection(project, EclipseProject)

        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e, "Could not fetch models of type 'EclipseProject'")
    }

    def "fails to retrieve model when participant is not a Gradle project"() {
        when:
        getUnwrappedModelsWithGradleConnection(projectDir.file("project-does-not-exist"), EclipseProject)

        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e,
            "Could not fetch models of type 'EclipseProject'",
            "The root project is not yet available for build")
    }

    def "does not search upwards for projects"() {
        given:
        def rootDir = projectDir.createDir("root")
        rootDir.file("settings.gradle") << "include 'project', 'a', 'b', 'c'"
        def project = rootDir.createDir("project")
        project.createFile("build.gradle")

        when:
        def models = getModelsWithGradleConnection(project, EclipseProject)

        then:
        // should only find 'project', not the other projects defined in root.
        models.size() == 1
        models[0].model.projectDirectory == project
    }
}
