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

import org.gradle.integtests.tooling.fixture.GradleConnectionToolingApiSpecification
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier
import org.gradle.tooling.internal.connection.DefaultProjectIdentifier
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.HasGradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject

class ModelsWithGradleProjectCrossVersionSpec extends GradleConnectionToolingApiSpecification {
    static projectScopedModels = [GradleProject, EclipseProject, HierarchicalEclipseProject]
    static buildScopedModels = [GradleBuild, IdeaProject, BasicIdeaProject]
    TestFile rootSingle
    TestFile rootMulti

    void setup() {
        rootSingle = singleProjectBuild("A")
        rootMulti = multiProjectBuild("B", ['x', 'y'])
    }

    def "ProjectConnection provides identified GradleBuild"() {
        when:
        def gradleBuild = getModelWithProjectConnection(rootMulti, GradleBuild)

        then:
        gradleBuild.buildIdentifier == new DefaultBuildIdentifier(rootMulti)
    }

    def "GradleConnection provides identified GradleBuild for each build"() {
        when:
        def gradleBuilds = getUnwrappedModelsWithGradleConnection(defineComposite(rootMulti, rootSingle), GradleBuild)

        then:
        gradleBuilds.size() == 2
        gradleBuilds.find { it.buildIdentifier == new DefaultBuildIdentifier(rootSingle) }
        gradleBuilds.find { it.buildIdentifier == new DefaultBuildIdentifier(rootMulti) }
    }

    def "ProjectConnection provides all GradleProjects for root of single project build"() {
        when:
        def gradleProjects = toGradleProjects(getModelWithProjectConnection(rootSingle, modelType))

        then:
        gradleProjects.size() == 1
        hasProject(gradleProjects, rootSingle, ':', 'A')

        where:
        modelType << buildScopedModels
    }

    def "GradleConnection provides GradleProjects for single project build"() {
        when:
        def gradleProjects = getUnwrappedModelsWithGradleConnection(rootSingle, modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 1
        hasProject(gradleProjects, rootSingle, ':', 'A')

        where:
        modelType << projectScopedModels
    }

    def "ProjectConnection provides all GradleProjects for root of multi-project build"() {
        when:
        def gradleProjects = toGradleProjects(getModelWithProjectConnection(rootMulti, modelType))

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << buildScopedModels
    }

    def "GradleConnection provides GradleProjects for multi-project build"() {
        when:
        def gradleProjects = getUnwrappedModelsWithGradleConnection(rootMulti, modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << projectScopedModels
    }

    def "ProjectConnection provides all GradleProjects for subproject of multi-project build"() {
        when:
        def rootDir = rootMulti.file("x")
        def gradleProjects = toGradleProjects(getModelWithProjectConnection(rootDir, modelType))

        then:
        gradleProjects.size() == 3
        hasParentProject(gradleProjects, rootDir, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootDir, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootDir, ':y', 'y', ':')

        where:
        modelType << buildScopedModels
    }

    def "GradleConnection provides GradleProjects for composite build"() {
        when:
        def gradleProjects = getUnwrappedModelsWithGradleConnection(defineComposite(rootSingle, rootMulti), modelType).collect { toGradleProject(it) }

        then:
        gradleProjects.size() == 4
        hasProject(gradleProjects, rootSingle, ':', 'A')
        hasParentProject(gradleProjects, rootMulti, ':', 'B', [':x', ':y'])
        hasChildProject(gradleProjects, rootMulti, ':x', 'x', ':')
        hasChildProject(gradleProjects, rootMulti, ':y', 'y', ':')

        where:
        modelType << projectScopedModels
    }

    def "ProjectConnection provides GradleProject for root of single project build"() {
        when:
        GradleProject project = toGradleProject(getModelWithProjectConnection(rootSingle, modelType))

        then:
        assertProject(project, rootSingle, ':', 'A', null, [])

        where:
        modelType << projectScopedModels
    }

    def "ProjectConnection provides GradleProject for root of multi-project build"() {
        when:
        GradleProject project = toGradleProject(getModelWithProjectConnection(rootMulti, modelType))

        then:
        assertProject(project, rootMulti, ':', 'B', null, [':x', ':y'])

        where:
        modelType << projectScopedModels
    }

    def "ProjectConnection provides GradleProject for subproject of multi-project build"() {
        given:
        def rootDir = rootMulti.file("x")

        when: "GradleProject is requested directly"
        GradleProject project = toGradleProject(getModelWithProjectConnection(rootDir, GradleProject))

        then: "Get the GradleProject model for the root project"
        assertProject(project, rootDir, ':', 'B', null, [':x', ':y'])

        when: "EclipseProject is requested"
        GradleProject projectFromEclipseProject = toGradleProject(getModelWithProjectConnection(rootDir, EclipseProject))

        then: "Has a GradleProject model for the subproject"
        assertProject(projectFromEclipseProject, rootDir, ':x', 'x', ':', [])
    }

    def "ProjectConnection provides GradleProject for subproject of multi-project build with --no-search-upwards"() {
        when:
        def rootDir = rootMulti.file("x")
        GradleProject project = toGradleProject(getModelWithProjectConnection(rootDir, modelType, false))

        then:
        assertProject(project, rootDir, ':', 'x', null, [])

        where:
        modelType << projectScopedModels
    }

    private static void hasProject(def projects, File rootDir, String path, String name) {
        hasProject(projects, rootDir, path, name, null, [])
    }

    private static void hasChildProject(def projects, File rootDir, String path, String name, String parentPath) {
        hasProject(projects, rootDir, path, name, parentPath, [])
    }

    private static void hasParentProject(def projects, File rootDir, String path, String name, List<String> childPaths) {
        hasProject(projects, rootDir, path, name, null, childPaths)
    }

    private static void hasProject(def projects, File rootDir, String path, String name, String parentPath, List<String> childPaths) {
        def project = projects.find {it.name == name}
        assert project != null :  "No project with name $name found"
        assertProject(project, rootDir, path, name, parentPath, childPaths)
     }

    private static void assertProject(def project, File rootDir, String path, String name, String parentPath, List<String> childPaths) {
        assert project.path == path
        assert project.name == name
        if (parentPath == null) {
            assert project.parent == null
        } else {
            assert project.parent.path == parentPath
        }
        // Order of children is not guaranteed for Gradle < 2.0
        assert project.children*.path as Set == childPaths as Set
        assert project.projectIdentifier == new DefaultProjectIdentifier(new DefaultBuildIdentifier(rootDir), path)
    }

    private static GradleProject toGradleProject(def model) {
        if (model instanceof GradleProject) {
            return model
        }
        if (model instanceof HasGradleProject) {
            return model.gradleProject
        }
        throw new IllegalArgumentException("Model type does not provide GradleProject")
    }

    private static def toGradleProjects(def buildScopeModel) {
        if (buildScopeModel instanceof GradleBuild) {
            return buildScopeModel.projects
        }
        if (buildScopeModel instanceof IdeaProject) {
            return buildScopeModel.modules*.gradleProject
        }
        throw new IllegalArgumentException("Model type does not provide GradleProjects")
    }
}
