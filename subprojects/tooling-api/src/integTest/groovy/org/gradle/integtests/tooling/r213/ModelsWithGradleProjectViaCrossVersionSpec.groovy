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

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject

class ModelsWithGradleProjectViaCrossVersionSpec extends ToolingApiSpecification implements ModelsWithGradleProjectSpecFixtures {

    def "Provides identified GradleBuild"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        when:
        def gradleBuild = loadToolingModel(modelType)

        then:
        gradleBuild.buildIdentifier == new DefaultBuildIdentifier(projectDir)
    }

    def "Provides all GradleProjects for root of single project build"() {
        setup:
        singleProjectBuildInRootFolder("A")

        when:
        def gradleProjects = toGradleProjects(loadToolingModel(modelType))

        then:
        gradleProjects.size() == 1
        hasProject(gradleProjects, projectDir, ':', 'A')

        where:
        modelType << buildScopedModels
    }

    def "Provides all GradleProjects for root of multi-project build"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        when:
        def gradleProjects = toGradleProjects(loadToolingModel(modelType))

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, projectDir, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, projectDir, ':x', 'x', ':')
        hasChildProject(gradleProjects, projectDir, ':y', 'y', ':')

        where:
        modelType << buildScopedModels
    }

    def "Provides all GradleProjects for subproject of multi-project build"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        def rootDir = projectDir.file("x")

        when:

        def gradleProjects = toGradleProjects(getModel(rootDir, modelType))

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootDir, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootDir, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootDir, ':y', 'y', ':')

        where:
        modelType << buildScopedModels
    }

    def "Provides GradleProject for root of single project build"() {
        setup:
        singleProjectBuildInRootFolder("A")

        when:
        GradleProject project = toGradleProject(loadToolingModel(modelType))

        then:
        assertProject(project, projectDir, ':', 'A', null, [])

        where:
        modelType << projectScopedModels
    }

    def "Provides GradleProject for root of multi-project build"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        when:
        GradleProject project = toGradleProject(loadToolingModel(modelType))

        then:
        assertProject(project, projectDir, ':', 'B', null, [':x', ':y'])

        where:
        modelType << projectScopedModels
    }

    def "Provides GradleProject for subproject of multi-project build"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])
        def rootDir = projectDir.file("x")

        when: "GradleProject is requested directly"
        GradleProject project = toGradleProject(getModel(rootDir, GradleProject))

        then: "Get the GradleProject model for the root project"
        assertProject(project, rootDir, ':', 'B', null, [':x', ':y'])

        when: "EclipseProject is requested"
        GradleProject projectFromEclipseProject = toGradleProject(getModel(rootDir, EclipseProject))

        then: "Has a GradleProject model for the subproject"
        assertProject(projectFromEclipseProject, rootDir, ':x', 'x', ':', [])
    }

    def "Provides GradleProject for subproject of multi-project build with --no-search-upwards"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])
        def rootDir = projectDir.file("x")

        when:
        GradleProject project = toGradleProject(getModel(rootDir, modelType, false))

        then:
        assertProject(project, rootDir, ':', 'x', null, [])

        where:
        modelType << projectScopedModels
    }

    private <T> T getModel(TestFile rootDir, Class<T> modelType, boolean searchUpwards = true) {
        return withConnection(rootDir, searchUpwards) { ProjectConnection it ->it.getModel(modelType) } as T
    }

    private <T> T withConnection(TestFile projectDir, boolean searchUpwards, Closure<T> cl) {
        GradleConnector connector = toolingApi.connector()
        connector.forProjectDirectory(projectDir.absoluteFile)
        ((DefaultGradleConnector) connector).searchUpwards(searchUpwards)
        return toolingApi.withConnection(connector, cl)
    }
}
