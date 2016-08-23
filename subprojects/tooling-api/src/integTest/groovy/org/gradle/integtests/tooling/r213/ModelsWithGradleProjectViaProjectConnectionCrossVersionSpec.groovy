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

import org.gradle.integtests.tooling.fixture.ProjectConnectionToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.GradleBuild

class ModelsWithGradleProjectViaProjectConnectionCrossVersionSpec extends ProjectConnectionToolingApiSpecification implements ModelsWithGradleProjectSpecFixtures {

    TestFile rootSingle
    TestFile rootMulti

    void setup() {
        rootSingle = singleProjectBuild("A")
        rootMulti = multiProjectBuild("B", ['x', 'y'])
    }

    def "Provides identified GradleBuild"() {
        when:
        def gradleBuild = getModel(rootMulti, GradleBuild)

        then:
        gradleBuild.buildIdentifier == new DefaultBuildIdentifier(rootMulti)
    }

    def "Provides all GradleProjects for root of single project build"() {
        when:
        def gradleProjects = toGradleProjects(getModel(rootSingle, modelType))

        then:
        gradleProjects.size() == 1
        hasProject(gradleProjects, rootSingle, ':', 'A')

        where:
        modelType << buildScopedModels
    }

    def "Provides all GradleProjects for root of multi-project build"() {
        when:
        def gradleProjects = toGradleProjects(getModel(rootMulti, modelType))

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << buildScopedModels
    }

    def "Provides all GradleProjects for subproject of multi-project build"() {
        when:
        def rootDir = rootMulti.file("x")
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
        when:
        GradleProject project = toGradleProject(getModel(rootSingle, modelType))

        then:
        assertProject(project, rootSingle, ':', 'A', null, [])

        where:
        modelType << projectScopedModels
    }

    def "Provides GradleProject for root of multi-project build"() {
        when:
        GradleProject project = toGradleProject(getModel(rootMulti, modelType))

        then:
        assertProject(project, rootMulti, ':', 'B', null, [':x', ':y'])

        where:
        modelType << projectScopedModels
    }

    def "Provides GradleProject for subproject of multi-project build"() {
        given:
        def rootDir = rootMulti.file("x")

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
        when:
        def rootDir = rootMulti.file("x")
        GradleProject project = toGradleProject(getModel(rootDir, modelType, false))

        then:
        assertProject(project, rootDir, ':', 'x', null, [])

        where:
        modelType << projectScopedModels
    }
}
