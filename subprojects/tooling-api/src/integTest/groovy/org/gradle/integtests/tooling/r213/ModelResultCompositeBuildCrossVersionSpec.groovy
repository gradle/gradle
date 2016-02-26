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
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.CompositeBuildException
import org.gradle.tooling.composite.BuildIdentity
import org.gradle.tooling.composite.ModelResult
import org.gradle.tooling.composite.ProjectIdentity
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject

class ModelResultCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    private Iterable<ModelResult> modelResults
    def setup() {
        embedCoordinatorAndParticipants = true
    }

    def "can correlate errors with build that caused it"() {
        given:
        def rootDirA = populate("A") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
            buildFile << """
                apply plugin: 'java'
                group = 'org.A'
                version = '1.0'
                throw new GradleException("Fail")
"""
        }
        when:
        def builder = createCompositeBuilder()
        def participantA = createGradleBuildParticipant(rootDirA)
        builder.addBuild(participantA)
        def connection = builder.build()
        def buildIdentity = participantA.toBuildIdentity()
        def otherBuildIdentity = createGradleBuildParticipant(file("B")).toBuildIdentity()
        and:
        modelResults = connection.getModels(EclipseProject)
        then:
        def e = thrown(CompositeBuildException)
        e.causedBy(buildIdentity)
        !e.causedBy(otherBuildIdentity)
    }

    def "can correlate models in a single project, single participant composite"() {
        given:
        def rootDirA = populate("A") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
            buildFile << """
                apply plugin: 'java'
                group = 'org.A'
                version = '1.0'
"""
        }
        and:
        def builder = createCompositeBuilder()
        def participantA = createGradleBuildParticipant(rootDirA)
        builder.addBuild(participantA)
        def connection = builder.build()
        def buildIdentity = participantA.toBuildIdentity()
        def projectIdentity = participantA.toProjectIdentity(":")
        when:
        modelResults = connection.getModels(EclipseProject)
        then:
        // We can locate the root project by its project identity
        assertSingleEclipseProject(findByProjectIdentity(projectIdentity), "A", ":")
        // We can locate all projects (just one in this case) by the build identity for the participant
        assertSingleEclipseProject(findByBuildIdentity(buildIdentity), "A", ":")

        when:
        // We can take the results from one model request and correlate it with other model requests by
        // the project and build identities
        def otherModelResults = connection.getModels(IdeaProject)
        then:
        containSameIdentifiers(otherModelResults)

        cleanup:
        connection?.close()
    }

    def "can correlate models in a multi-project, single participant composite"() {
        given:
        def rootDirA = populate("A") {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'x', 'y'
            """

            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'org.A'
                    version = '1.0'
                }
            """
        }
        and:
        def builder = createCompositeBuilder()
        def participantA = createGradleBuildParticipant(rootDirA)
        builder.addBuild(participantA)
        def connection = builder.build()
        def buildIdentity = participantA.toBuildIdentity()
        def projectIdentity = participantA.toProjectIdentity(":")
        def projectIdentityX = participantA.toProjectIdentity(":x")
        def projectIdentityY = participantA.toProjectIdentity(":y")

        when:
        modelResults = connection.getModels(EclipseProject)
        then:
        // We can locate each project by its project identity
        assertSingleEclipseProject(findByProjectIdentity(projectIdentity), "A", ":")
        assertSingleEclipseProject(findByProjectIdentity(projectIdentityX), "A", ":x")
        assertSingleEclipseProject(findByProjectIdentity(projectIdentityY), "A", ":y")

        // We can locate all projects by the build identity for the participant
        assertContainsEclipseProjects(findByBuildIdentity(buildIdentity), "A", ":", ":x", ":y")

        when:
        // We can take the results from one model request and correlate it with other model requests by
        // the project and build identities
        def otherModelResults = connection.getModels(IdeaProject)
        then:
        containSameIdentifiers(otherModelResults)

        cleanup:
        connection?.close()
    }

    def "can correlate models in a single and multi-project, multi-participant composite"() {
        given:
        def rootDirA = populate("A") {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
            """

            buildFile << """
                apply plugin: 'java'
                group = 'org.A'
                version = '1.0'
            """
        }
        def rootDirB = populate("B") {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'x', 'y'
            """

            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'org.B'
                    version = '1.0'
                }
            """
        }
        and:
        def builder = createCompositeBuilder()
        def participantA = createGradleBuildParticipant(rootDirA)
        def participantB = createGradleBuildParticipant(rootDirB)
        builder.addBuilds(participantA, participantB)
        def connection = builder.build()

        def buildIdentityA = participantA.toBuildIdentity()
        def projectIdentityA = participantA.toProjectIdentity(":")

        def buildIdentityB = participantB.toBuildIdentity()
        def projectIdentityB = participantB.toProjectIdentity(":")
        def projectIdentityBX = participantB.toProjectIdentity(":x")
        def projectIdentityBY = participantB.toProjectIdentity(":y")

        when:
        modelResults = connection.getModels(EclipseProject)
        then:
        // We can locate each project by its project identity
        assertSingleEclipseProject(findByProjectIdentity(projectIdentityA), "A", ":")
        assertSingleEclipseProject(findByProjectIdentity(projectIdentityB), "B", ":")
        assertSingleEclipseProject(findByProjectIdentity(projectIdentityBX), "B", ":x")
        assertSingleEclipseProject(findByProjectIdentity(projectIdentityBY), "B", ":y")

        // We can locate all projects by the build identity for the participant
        assertContainsEclipseProjects(findByBuildIdentity(buildIdentityA), "A", ":")
        assertContainsEclipseProjects(findByBuildIdentity(buildIdentityB), "B", ":", ":x", ":y")

        when:
        // We can take the results from one model request and correlate it with other model requests by
        // the project and build identities
        def otherModelResults = connection.getModels(IdeaProject)
        then:
        containSameIdentifiers(otherModelResults)

        cleanup:
        connection?.close()
    }

    void assertSingleEclipseProject(Iterable<ModelResult<EclipseProject>> modelResults, String rootProjectName, String projectPath) {
        assertContainsEclipseProjects(modelResults, rootProjectName, projectPath)
    }

    void assertContainsEclipseProjects(Iterable<ModelResult<EclipseProject>> modelResults, String rootProjectName, String... projectPaths) {
        assert modelResults.size() == projectPaths.size()
        projectPaths.each { projectPath ->
            Iterable<EclipseProject> eclipseProjects = unwrap(modelResults)
            assert eclipseProjects.every { eclipseProject ->
                EclipseProject rootProject = eclipseProject
                while (rootProject.parent!=null) {
                    rootProject = rootProject.parent
                }
                rootProject.name == rootProjectName
            }
            assert eclipseProjects.any { it.gradleProject.path == projectPath }
        }
    }

    def findByBuildIdentity(BuildIdentity buildIdentity) {
        modelResults.findAll { buildIdentity.equals(it.projectIdentity.build) }
    }

    def findByProjectIdentity(ProjectIdentity projectIdentity) {
        modelResults.findAll { projectIdentity.equals(it.projectIdentity) }
    }

    void containSameIdentifiers(Iterable<ModelResult> otherModelResults) {
        // should contain the same number of results
        assert otherModelResults.size() == modelResults.size()

        def projectIdentities = modelResults.collect { it.projectIdentity }
        def otherProjectIdentities = otherModelResults.collect { it.projectIdentity }
        assert projectIdentities.containsAll(otherProjectIdentities)

        def buildIdentities = modelResults.collect { it.projectIdentity.build }
        def otherBuildIdentities = otherModelResults.collect { it.projectIdentity.build }
        assert buildIdentities.containsAll(otherBuildIdentities)
    }
}
