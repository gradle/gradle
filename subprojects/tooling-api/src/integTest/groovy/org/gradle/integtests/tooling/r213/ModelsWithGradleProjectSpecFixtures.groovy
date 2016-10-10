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

import org.gradle.tooling.internal.gradle.BasicGradleProject
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.HasGradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject

trait ModelsWithGradleProjectSpecFixtures {

    static List<Class<?>> getProjectScopedModels() {
        [GradleProject, EclipseProject, HierarchicalEclipseProject]
    }

    static List<Class<?>> getBuildScopedModels() {
        [GradleBuild, IdeaProject, BasicIdeaProject]
    }

    static void hasProject(def projects, File rootDir, String path, String name) {
        hasProject(projects, rootDir, path, name, null, [])
    }

    static void hasChildProject(def projects, File rootDir, String path, String name, String parentPath) {
        hasProject(projects, rootDir, path, name, parentPath, [])
    }

    static void hasParentProject(def projects, File rootDir, String path, String name, List<String> childPaths) {
        hasProject(projects, rootDir, path, name, null, childPaths)
    }

    static void hasProject(def projects, File rootDir, String path, String name, String parentPath, List<String> childPaths) {
        def project = projects.find {it.name == name}
        assert project != null :  "No project with name $name found"
        assertProject(project, rootDir, path, name, parentPath, childPaths)
     }

    static void assertProject(def project, File rootDir, String path, String name, String parentPath, List<String> childPaths) {
        assert project.path == path
        assert project.name == name
        if (parentPath == null) {
            assert project.parent == null
        } else {
            assert project.parent.path == parentPath
        }
        // Order of children is not guaranteed for Gradle < 2.0
        assert project.children*.path as Set == childPaths as Set
        assert project.projectIdentifier.projectPath == path
        assert project.projectIdentifier.buildIdentifier.rootDir == rootDir
    }

    static GradleProject toGradleProject(def model) {
        if (model instanceof GradleProject) {
            return model
        }
        if (model instanceof HasGradleProject) {
            return model.gradleProject
        }
        throw new IllegalArgumentException("Model type does not provide GradleProject")
    }

    static Set<? extends BasicGradleProject> toGradleProjects(def buildScopeModel) {
        if (buildScopeModel instanceof GradleBuild) {
            return buildScopeModel.projects
        }
        if (buildScopeModel instanceof IdeaProject) {
            return buildScopeModel.modules*.gradleProject
        }
        throw new IllegalArgumentException("Model type does not provide GradleProjects")
    }
}
