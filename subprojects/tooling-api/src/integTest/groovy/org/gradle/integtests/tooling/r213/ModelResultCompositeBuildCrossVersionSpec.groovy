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
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.connection.FailedModelResult
import org.gradle.tooling.connection.ModelResults
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier
import org.gradle.tooling.internal.connection.DefaultProjectIdentifier
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.CollectionUtils

class ModelResultCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    private ModelResults<EclipseProject> modelResults

    def "can correlate exceptions in composite with multiple single-project participants"() {
        given:
        def rootDirA = singleProjectBuild("A") {
            buildFile << "throw new GradleException('Failure in A')"
        }
        def rootDirB = singleProjectBuild("B")
        def rootDirC = singleProjectBuild("C") {
            buildFile << "throw new GradleException('Different failure in C')"
        }
        when:
        withCompositeConnection([rootDirA, rootDirB, rootDirC]) { connection ->
            modelResults = connection.getModels(EclipseProject)
        }

        then:
        def resultA = findFailureByBuildIdentity(rootDirA)
        assertFailure(resultA.failure,
            "Could not fetch models of type 'EclipseProject'",
            "A problem occurred evaluating root project 'A'.",
            "Failure in A")

        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirB, ':'), 'B', ':')

        def resultC = findFailureByBuildIdentity(rootDirC)
        assertFailure(resultC.failure,
            "Could not fetch models of type 'EclipseProject'",
            "A problem occurred evaluating root project 'C'.",
            "Different failure in C")
    }

    def "can correlate exceptions in composite with multiple multi-project participants"() {
        given:
        def rootDirA = multiProjectBuild("A", ['ax', 'ay']) {
            file("ax/build.gradle") << """
                throw new GradleException("Failure in A::ax")
"""
        }
        def rootDirB = multiProjectBuild("B", ['bx', 'by'])

        when:
        withCompositeConnection([rootDirA, rootDirB]) {
            modelResults = it.getModels(EclipseProject)
        }

        then:
        // when the build cannot be configured, we return only a failure for the root project
        def resultA = findFailureByBuildIdentity(rootDirA)
        assertFailure(resultA.failure,
            "Could not fetch models of type 'EclipseProject'",
            "A problem occurred evaluating project ':ax'.",
            "Failure in A::ax")
        // No models are returned
        findModelsByBuildIdentity(rootDirA) == []

        assertContainsEclipseProjects(findModelsByBuildIdentity(rootDirB), "B", ":", ":bx", ":by")
    }

    def "can correlate models in a single project, single participant composite"() {
        given:
        def rootDirA = singleProjectBuild("A")

        when:
        Iterable<IdeaProject> ideaProjects = []
        withCompositeConnection([rootDirA]) {
            modelResults = it.getModels(EclipseProject)
            ideaProjects = it.getModels(IdeaProject)*.model
        }
        then:
        // We can locate the root project by its project identity
        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirA, ':'), "A", ":")
        // We can locate all projects (just one in this case) by the build identity for the participant
        assertSingleEclipseProject(findModelsByBuildIdentity(rootDirA), "A", ":")

        and:
        ideaProjects.size() == 1
        containSameIdentifiers(ideaModuleProjectIdentifiers(ideaProjects))
    }

    def "can correlate models in a multi-project, single participant composite"() {
        given:
        def rootDirA = multiProjectBuild("A", ['x', 'y'])

        when:
        def otherHierarchicalModelResults = []
        def otherPerBuildModelResults = []
        def ideaProjects = []
        withCompositeConnection([rootDirA]) {
            modelResults = it.getModels(EclipseProject)
            otherHierarchicalModelResults = it.getModels(GradleProject)*.model*.identifier
            otherPerBuildModelResults = it.getModels(BuildInvocations)*.model*.gradleProjectIdentifier
            ideaProjects = it.getModels(IdeaProject)*.model
        }

        then:
        // We can locate each project by its project identity
        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirA, ':'), "A", ":")
        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirA, ':x'), "A", ":x")
        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirA, ':y'), "A", ":y")

        // We can locate all projects by the build identity for the participant
        assertContainsEclipseProjects(findModelsByBuildIdentity(rootDirA), "A", ":", ":x", ":y")

        and:
        containSameIdentifiers(otherHierarchicalModelResults)
        containSameIdentifiers(otherPerBuildModelResults)

        and:
        ideaProjects.size() == 1
        containSameIdentifiers(ideaModuleProjectIdentifiers(ideaProjects))
    }

    def "can correlate models in a single and multi-project, multi-participant composite"() {
        given:
        def rootDirA = singleProjectBuild("A")
        def rootDirB = multiProjectBuild("B", ['x', 'y'])

        when:
        def otherHierarchicalModelResults = []
        def otherPerBuildModelResults = []
        def ideaProjects = []
        withCompositeConnection([rootDirA, rootDirB]) {
            modelResults = it.getModels(EclipseProject)
            otherHierarchicalModelResults = it.getModels(GradleProject)*.model*.identifier
            otherPerBuildModelResults = it.getModels(BuildInvocations)*.model*.gradleProjectIdentifier
            ideaProjects = it.getModels(IdeaProject)*.model
        }

        then:
        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirA, ':'), "A", ":")
        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirB, ':'), "B", ":")
        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirB, ':x'), "B", ":x")
        assertSingleEclipseProject(findModelsByProjectIdentity(rootDirB, ':y'), "B", ":y")

        assertContainsEclipseProjects(findModelsByBuildIdentity(rootDirA), "A", ":")
        assertContainsEclipseProjects(findModelsByBuildIdentity(rootDirB), "B", ":", ":x", ":y")

        and:
        containSameIdentifiers(otherHierarchicalModelResults)
        containSameIdentifiers(otherPerBuildModelResults)

        and:
        ideaProjects.size() == 2
        containSameIdentifiers(ideaModuleProjectIdentifiers(ideaProjects))
    }

    void assertSingleEclipseProject(Iterable<EclipseProject> modelResults, String rootProjectName, String projectPath) {
        assertContainsEclipseProjects(modelResults, rootProjectName, projectPath)
    }

    void assertContainsEclipseProjects(Iterable<EclipseProject> eclipseProjects, String rootProjectName, String... projectPaths) {
        assert eclipseProjects.size() == projectPaths.size()
        projectPaths.each { projectPath ->
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

    private findModelsByProjectIdentity(File rootDir, String projectPath) {
        def projectIdentity = new DefaultProjectIdentifier(new DefaultBuildIdentifier(rootDir), projectPath)
        def results = modelResults.findAll { it.failure == null && projectIdentity.equals(it.model.gradleProject.identifier) }
        return results*.model
    }

    private findModelsByBuildIdentity(File rootDir) {
        BuildIdentifier buildIdentity = new DefaultBuildIdentifier(rootDir)
        def results = modelResults.findAll { it.failure == null && buildIdentity.equals(it.model.gradleProject.identifier.build) }
        return results*.model
    }

    private findFailureByBuildIdentity(File rootDir) {
        BuildIdentifier buildIdentity = new DefaultBuildIdentifier(rootDir)
        def failures = modelResults.findAll { it instanceof FailedModelResult && buildIdentity.equals(it.buildIdentifier) }
        return CollectionUtils.single(failures)
    }

    private ideaModuleProjectIdentifiers(Iterable<IdeaProject> ideaProject) {
        def modules = ideaProject*.modules.flatten()
        return modules.collect { it.gradleProject.identifier }
    }

    void containSameIdentifiers(Iterable<ProjectIdentifier> otherModelResults) {
        // should contain the same number of results
        assert otherModelResults.size() == modelResults.size()

        def projectIdentities = modelResults*.model.collect { it.gradleProject.identifier }
        def otherProjectIdentities = otherModelResults.collect { it }
        assert projectIdentities.containsAll(otherProjectIdentities)

        def buildIdentities = modelResults*.model.collect { it.gradleProject.identifier.build }
        def otherBuildIdentities = otherModelResults.collect { it.build }
        assert buildIdentities.containsAll(otherBuildIdentities)
    }
}
