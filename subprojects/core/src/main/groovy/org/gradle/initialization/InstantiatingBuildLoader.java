/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;

public class InstantiatingBuildLoader implements BuildLoader {
    private final IProjectFactory projectFactory;

    public InstantiatingBuildLoader(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }

    /**
     * Creates the {@link org.gradle.api.internal.GradleInternal} and {@link ProjectInternal} instances for the given root project,
     * ready for the projects to be configured.
     */
    public void load(ProjectDescriptor rootProjectDescriptor, ProjectDescriptor defaultProject, GradleInternal gradle, ClassLoaderScope baseClassLoaderScope) {
        createProjects(rootProjectDescriptor, gradle, baseClassLoaderScope);
        attachDefaultProject(defaultProject, gradle);
    }

    private void attachDefaultProject(ProjectDescriptor defaultProject, GradleInternal gradle) {
        gradle.setDefaultProject(gradle.getRootProject().getProjectRegistry().getProject(defaultProject.getPath()));
    }

    private void createProjects(ProjectDescriptor rootProjectDescriptor, GradleInternal gradle, ClassLoaderScope baseClassLoaderScope) {
        ProjectInternal rootProject = projectFactory.createProject(rootProjectDescriptor, null, gradle, baseClassLoaderScope.createChild(), baseClassLoaderScope);
        gradle.setRootProject(rootProject);
        addProjects(rootProject, rootProjectDescriptor, gradle, baseClassLoaderScope);
    }

    private void addProjects(ProjectInternal parent, ProjectDescriptor parentProjectDescriptor, GradleInternal gradle, ClassLoaderScope baseClassLoaderScope) {
        for (ProjectDescriptor childProjectDescriptor : parentProjectDescriptor.getChildren()) {
            ProjectInternal childProject = projectFactory.createProject(childProjectDescriptor, parent, gradle, parent.getClassLoaderScope().createChild(), baseClassLoaderScope);
            addProjects(childProject, childProjectDescriptor, gradle, baseClassLoaderScope);
        }
    }
}
