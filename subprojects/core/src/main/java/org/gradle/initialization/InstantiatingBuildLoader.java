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

    @Override
    public void load(SettingsInternal settings, GradleInternal gradle) {
        createProjects(gradle, settings.getRootProject());
        attachDefaultProject(gradle, settings.getDefaultProject());
    }

    private void attachDefaultProject(GradleInternal gradle, ProjectDescriptor defaultProjectDescriptor) {
        ProjectInternal rootProject = gradle.getRootProject();
        ProjectRegistry<ProjectInternal> projectRegistry = rootProject.getProjectRegistry();

        String defaultProjectPath = defaultProjectDescriptor.getPath();
        ProjectInternal defaultProject = projectRegistry.getProject(defaultProjectPath);
        if (defaultProject == null) {
            throw new IllegalStateException("Did not find project with path " + defaultProjectPath);
        }

        gradle.setDefaultProject(defaultProject);
    }

    private void createProjects(GradleInternal gradle, ProjectDescriptor rootProjectDescriptor) {
        ClassLoaderScope baseProjectClassLoaderScope = gradle.baseProjectClassLoaderScope();
        ClassLoaderScope rootProjectClassLoaderScope = baseProjectClassLoaderScope.createChild("root-project");

        ProjectInternal rootProject = projectFactory.createProject(gradle, rootProjectDescriptor, null, rootProjectClassLoaderScope, baseProjectClassLoaderScope);
        gradle.setRootProject(rootProject);

        createChildProjectsRecursively(gradle, rootProject, rootProjectDescriptor, rootProjectClassLoaderScope, baseProjectClassLoaderScope);
    }

    private void createChildProjectsRecursively(GradleInternal gradle, ProjectInternal parentProject, ProjectDescriptor parentProjectDescriptor, ClassLoaderScope parentProjectClassLoaderScope, ClassLoaderScope baseProjectClassLoaderScope) {
        for (ProjectDescriptor childProjectDescriptor : parentProjectDescriptor.getChildren()) {
            ClassLoaderScope childProjectClassLoaderScope = parentProjectClassLoaderScope.createChild("project-" + childProjectDescriptor.getName());
            ProjectInternal childProject = projectFactory.createProject(gradle, childProjectDescriptor, parentProject, childProjectClassLoaderScope, baseProjectClassLoaderScope);

            createChildProjectsRecursively(gradle, childProject, childProjectDescriptor, childProjectClassLoaderScope, baseProjectClassLoaderScope);
        }
    }
}
