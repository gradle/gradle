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
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;

public class InstantiatingBuildLoader implements BuildLoader {
    private final IProjectFactory projectFactory;

    public InstantiatingBuildLoader(IProjectFactory projectFactory) {
        this.projectFactory = projectFactory;
    }

    /**
     * Creates the {@link org.gradle.api.internal.GradleInternal} and {@link ProjectInternal} instances for the given root project, ready for the projects to be configured.
     */
    @Override
    public void load(SettingsInternal settings, GradleInternal gradle) {
        createProjects(settings.getRootProject(), gradle);
        attachDefaultProject(settings.getDefaultProject(), gradle);
    }

    private void attachDefaultProject(ProjectDescriptor defaultProjectDescriptor, GradleInternal gradle) {
        ProjectInternal rootProject = gradle.getRootProject();
        ProjectRegistry<ProjectInternal> projectRegistry = rootProject.getProjectRegistry();
        String defaultProjectPath = defaultProjectDescriptor.getPath();
        ProjectInternal defaultProject = projectRegistry.getProject(defaultProjectPath);
        if (defaultProject == null) {
            throw new IllegalStateException("Did not find project with path " + defaultProjectPath);
        }

        gradle.setDefaultProject(defaultProject);
    }

    private void createProjects(ProjectDescriptor rootProjectDescriptor, GradleInternal gradle) {
        ClassLoaderScope baseProjectClassLoaderScope = gradle.baseProjectClassLoaderScope();
        ClassLoaderScope classLoaderScope = baseProjectClassLoaderScope.createChild("root-project");
        ProjectInternal rootProject = projectFactory.createProject(rootProjectDescriptor, null, gradle, classLoaderScope, baseProjectClassLoaderScope);
        gradle.setRootProject(rootProject);
        addProjects(rootProject, rootProjectDescriptor, gradle, classLoaderScope);
    }

    private void addProjects(ProjectInternal parent, ProjectDescriptor parentProjectDescriptor, GradleInternal gradle, ClassLoaderScope rootProjectClassLoaderScope) {
        for (ProjectDescriptor childProjectDescriptor : parentProjectDescriptor.getChildren()) {
            ClassLoaderScope classLoaderScope = rootProjectClassLoaderScope.createChild("project-" + childProjectDescriptor.getName());
            ProjectInternal childProject = projectFactory.createProject(childProjectDescriptor, parent, gradle, classLoaderScope, rootProjectClassLoaderScope);
            addProjects(childProject, childProjectDescriptor, gradle, rootProjectClassLoaderScope);
        }
    }
}
