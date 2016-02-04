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

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Ignore

/**
 * Builds a composite with a single project.
 */
@Ignore
class SingleProjectCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    def "can create composite of a single multi-project build"() {
        given:
        def singleBuild = populate("single-build") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'a', 'b', 'c'
"""
        }
        expect:
        withCompositeConnection(singleBuild) { connection ->
            def models = connection.getModels(EclipseProject)
            assert models.size() == 4
            containsProjects(models, [':', ':a', ':b', ':c'])
            assert rootProjects(models).size() == 1
        }
    }

    def "can create composite of a single single-project build"() {
        given:
        def singleBuild = populate("single-build") {
            buildFile << """
                apply plugin: 'java'
                group = 'group'
                version = '1.0'
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
"""
        }
        expect:
        withCompositeConnection(singleBuild) { connection ->
            def models = connection.getModels(EclipseProject)
            assert models.size() == 1
            containsProjects(models, [':'])
            assert rootProjects(models).size() == 1
        }
    }

    def "sees changes to composite build when projects are added"() {
        given:
        def singleBuild = populate("single-build") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
"""
        }
        def composite = createComposite(singleBuild)

        when:
        def firstRetrieval = composite.getModels(EclipseProject)

        then:
        firstRetrieval.size() == 1
        assert rootProjects(firstRetrieval).size() == 1
        containsProjects(firstRetrieval, [':'])

        when:
        // make project a multi-project build
        populate("single-build") {
            settingsFile << """
                include 'a'
"""
        }
        and:
        def secondRetrieval = composite.getModels(EclipseProject)

        then:
        secondRetrieval.size() == 2
        assert rootProjects(secondRetrieval).size() == 1
        containsProjects(secondRetrieval, [':', ':a'])

        when:
        // adding more projects to multi-project build
        populate("single-build") {
            settingsFile << "include 'b', 'c'"
        }
        and:
        def thirdRetrieval = composite.getModels(EclipseProject)

        then:
        thirdRetrieval.size() == 4
        assert rootProjects(thirdRetrieval).size() == 1
        containsProjects(thirdRetrieval, [':', ':a', ':b', ':c'])

        when:
        // remove the existing project
        singleBuild.deleteDir()

        and:
        def fourthRetrieval = composite.getModels(EclipseProject)

        then:
        def e = thrown(BuildException)
        e.getMessage().contains("Could not fetch model of type 'EclipseProject'")
        def underlyingCause = e.getCause().getCause()
        underlyingCause.getMessage().contains("single-build' does not exist")

        cleanup:
        composite?.close()
    }

    @Ignore
    def "can retrieve EclipseProject from composite for Gradle #gradleVersion"() {
        given:
        def singleBuild = populate("single-build") {
            buildFile << """
                apply plugin: 'java'
                group = 'group'
                version = '1.0'
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
"""
        }
        and:
        def builder = createCompositeBuilder()
        builder.addBuild(singleBuild, gradleVersion)
        def connection = builder.build()
        expect:
        def models = connection.getModels(EclipseProject)
        models.size() == 1

        where:
        gradleVersion << ["1.0", "2.0", "2.10"]
    }

    Set<EclipseProject> rootProjects(Set<EclipseProject> projects) {
        projects.findAll { it.parent == null }
    }

    void containsProjects(models, projects) {
        def projectsFoundByPath = models.collect { it.gradleProject.path }
        assert projectsFoundByPath.containsAll(projects)
    }
}
