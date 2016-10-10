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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.eclipse.EclipseProject

/**
 * Tests composites with multiple participants.
 */
@TargetGradleVersion(ToolingApiVersions.SUPPORTS_MULTI_MODEL)
class MultiProjectCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification {

    def "can create a composite of two single-project builds"() {
        given:
        def singleBuild1 = singleProjectBuildInSubfolder("single-build-1")
        def singleBuild2 = singleProjectBuildInSubfolder("single-build-2")
        includeBuilds(singleBuild1, singleBuild2)

        when:
        def models = getUnwrappedModels(EclipseProject)

        then:
        models.size() == 3
        rootProjects(models).size() == 3
        containsProjects(models, [':', ':', ':'])
    }

    def "can create composite of a two multi-project builds"() {
        given:
        def multiBuild1 = multiProjectBuildInSubFolder("multi-build-1", ['a1', 'b1', 'c1'])
        def multiBuild2 = multiProjectBuildInSubFolder("multi-build-2", ['a2', 'b2', 'c2'])
        includeBuilds(multiBuild1, multiBuild2)

        when:
        def models = getUnwrappedModels(EclipseProject)

        then:
        models.size() == 9
        rootProjects(models).size() == 3
        containsProjects(models, [':', ':', ':a1', ':b1', ':c1', ':', ':a2', ':b2', ':c2'])
    }

    def "can create composite of a single-project and multi-project builds"() {
        given:
        def singleBuild = singleProjectBuildInSubfolder("single-build-1")
        def multiBuild = multiProjectBuildInSubFolder("multi-build-1", ['a1', 'b1', 'c1'])
        includeBuilds(singleBuild, multiBuild)

        when:
        def models = getUnwrappedModels(EclipseProject)

        then:
        models.size() == 6
        rootProjects(models).size() == 3
        containsProjects(models, [':', ':', ':', ':a1', ':b1', ':c1'])
    }

    def "sees changes to composite build when projects are added"() {
        given:
        def singleBuild = singleProjectBuildInSubfolder("single-build")
        def multiBuild = multiProjectBuildInSubFolder("multi-build-1", ['a1', 'b1', 'c1'])
        includeBuilds(singleBuild, multiBuild)
        def connector = toolingApi.connector()
        connector.forProjectDirectory(projectDir)
        def connection = connector.connect()

        when:
        def firstRetrieval = unwrap(connection.getModels(EclipseProject))

        then:
        firstRetrieval.size() == 6
        rootProjects(firstRetrieval).size() == 3
        containsProjects(firstRetrieval, [':', ':', ':', ':a1', ':b1', ':c1'])

        when:
        // make single-project a multi-project build
        singleBuild.settingsFile << "\ninclude 'a2'"

        and:
        def secondRetrieval = unwrap(connection.getModels(EclipseProject))

        then:
        secondRetrieval.size() == 7
        rootProjects(secondRetrieval).size() == 3
        containsProjects(secondRetrieval, [':', ':', ':a2', ':', ':a1', ':b1', ':c1'])

        when:
        // adding more projects to multi-project build
        singleBuild.settingsFile << "\ninclude 'b2', 'c2'"

        and:
        def thirdRetrieval = unwrap(connection.getModels(EclipseProject))

        then:
        thirdRetrieval.size() == 9
        rootProjects(thirdRetrieval).size() == 3
        containsProjects(thirdRetrieval, [':', ':', ':a2', ':b2', ':c2', ':', ':a1', ':b1', ':c1'])

        when:
        // remove one participant
        singleBuild.deleteDir()

        and:
        def fourthRetrieval = unwrap(connection.getModels(EclipseProject))

        then:
        def e = thrown(GradleConnectionException)
        e.printStackTrace()
        assertFailure(e, "Could not fetch models of type 'EclipseProject'",
            "Included build '$singleBuild' does not exist.")

        cleanup:
        connection?.close()
    }

    Iterable<EclipseProject> rootProjects(Iterable<EclipseProject> projects) {
        projects.findAll { it.parent == null }
    }

    void containsProjects(models, projects) {
        def projectsFoundByPath = models.collect { it.gradleProject.path }
        assert projectsFoundByPath.size() == projects.size()
        assert projectsFoundByPath.containsAll(projects) // TODO (donat) shouldn't the project ordering be always the same?
    }
}
