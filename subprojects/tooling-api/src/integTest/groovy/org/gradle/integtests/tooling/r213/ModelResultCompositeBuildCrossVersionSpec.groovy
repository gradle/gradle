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
import org.gradle.tooling.connection.BuildIdentity
import org.gradle.tooling.connection.ModelResult
import org.gradle.tooling.internal.protocol.DefaultProjectIdentity
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.CollectionUtils

class ModelResultCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    private Iterable<ModelResult> modelResults

    def "can correlate exceptions in composite with multiple single-project participants"() {
        given:
        def rootDirA = populate("A") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
            buildFile << """
                apply plugin: 'java'
                group = 'org.A'
                version = '1.0'
                throw new GradleException("Failure in A")
"""
        }
        def rootDirB = populate("B") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        def rootDirC = populate("C") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
            buildFile << """
                apply plugin: 'java'
                throw new GradleException("Different failure in C")
"""
        }
        when:
        def builder = createCompositeBuilder()
        def participantA = addCompositeParticipant(builder, rootDirA)
        def participantB = addCompositeParticipant(builder, rootDirB)
        def participantC = addCompositeParticipant(builder, rootDirC)
        def connection = builder.build()

        and:
        modelResults = connection.getModels(EclipseProject)

        then:
        def resultA = CollectionUtils.single(findByProjectIdentity(participantA, ':'))
        assertFailure(resultA.failure,
            "Could not fetch models of type 'EclipseProject'",
            "A problem occurred evaluating root project 'A'.",
            "Failure in A")

        def resultB = findByProjectIdentity(participantB, ':')
        assertSingleEclipseProject(resultB, 'B', ':')

        def resultC = CollectionUtils.single(findByProjectIdentity(participantC, ':'))
        assertFailure(resultC.failure,
            "Could not fetch models of type 'EclipseProject'",
            "A problem occurred evaluating root project 'C'.",
            "Different failure in C")
    }

    def "can correlate exceptions in composite with multiple multi-project participants"() {
        given:
        def rootDirA = populate("A") {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'ax', 'ay'
            """

            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'org.A'
                    version = '1.0'
                }
            """
            file("ax/build.gradle") << """
                throw new GradleException("Failure in A::ax")
"""
        }
        def rootDirB = populate("B") {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'bx', 'by'
            """

            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'org.B'
                    version = '1.0'
                }
            """
        }
        when:
        def builder = createCompositeBuilder()
        def participantA = addCompositeParticipant(builder, rootDirA)
        def participantB = addCompositeParticipant(builder, rootDirB)
        def connection = builder.build()

        and:
        modelResults = connection.getModels(EclipseProject)

        then:
        // when the build cannot be configured, we return only a failure for the root project
        def resultA = CollectionUtils.single(findByProjectIdentity(participantA, ':'))
        assertFailure(resultA.failure,
            "Could not fetch models of type 'EclipseProject'",
            "A problem occurred evaluating project ':ax'.",
            "Failure in A::ax")
        // cannot find a project by something other than the root project
        findByProjectIdentity(participantA, ":ax") == []

        def resultB = findByProjectIdentity(participantB, ':bx')
        assertSingleEclipseProject(resultB, 'B', ':bx')
        assertContainsEclipseProjects(findByBuildIdentity(participantB), "B", ":", ":bx", ":by")
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
        def participantA = addCompositeParticipant(builder, rootDirA)
        def connection = builder.build()
        when:
        modelResults = connection.getModels(EclipseProject)
        then:
        // We can locate the root project by its project identity
        assertSingleEclipseProject(findByProjectIdentity(participantA, ':'), "A", ":")
        // We can locate all projects (just one in this case) by the build identity for the participant
        assertSingleEclipseProject(findByBuildIdentity(participantA), "A", ":")

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
        def participantA = addCompositeParticipant(builder, rootDirA)
        def connection = builder.build()

        when:
        modelResults = connection.getModels(EclipseProject)
        then:
        // We can locate each project by its project identity
        assertSingleEclipseProject(findByProjectIdentity(participantA, ':'), "A", ":")
        assertSingleEclipseProject(findByProjectIdentity(participantA, ':x'), "A", ":x")
        assertSingleEclipseProject(findByProjectIdentity(participantA, ':y'), "A", ":y")

        // We can locate all projects by the build identity for the participant
        assertContainsEclipseProjects(findByBuildIdentity(participantA), "A", ":", ":x", ":y")

        when:
        // We can take the results from one model request and correlate it with other model requests by
        // the project and build identities
        def otherHierarchicalModelResults = connection.getModels(GradleProject)
        def otherPerBuildModelResults = connection.getModels(BuildEnvironment)
        then:
        containSameIdentifiers(otherHierarchicalModelResults)
        containSameIdentifiers(otherPerBuildModelResults)

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
        def participantA = addCompositeParticipant(builder, rootDirA)
        def participantB = addCompositeParticipant(builder, rootDirB)
        def connection = builder.build()

        when:
        modelResults = connection.getModels(EclipseProject)
        then:
        // We can locate each project by its project identity
        assertSingleEclipseProject(findByProjectIdentity(participantA, ':'), "A", ":")
        assertSingleEclipseProject(findByProjectIdentity(participantB, ':'), "B", ":")
        assertSingleEclipseProject(findByProjectIdentity(participantB, ':x'), "B", ":x")
        assertSingleEclipseProject(findByProjectIdentity(participantB, ':y'), "B", ":y")

        // We can locate all projects by the build identity for the participant
        assertContainsEclipseProjects(findByBuildIdentity(participantA), "A", ":")
        assertContainsEclipseProjects(findByBuildIdentity(participantB), "B", ":", ":x", ":y")

        when:
        // We can take the results from one model request and correlate it with other model requests by
        // the project and build identities
        def otherHierarchicalModelResults = connection.getModels(GradleProject)
        def otherPerBuildModelResults = connection.getModels(BuildEnvironment)
        then:
        containSameIdentifiers(otherHierarchicalModelResults)
        containSameIdentifiers(otherPerBuildModelResults)

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

    def findByProjectIdentity(BuildIdentity buildIdentity, String projectPath) {
        def projectIdentity = new DefaultProjectIdentity(buildIdentity, projectPath)
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
