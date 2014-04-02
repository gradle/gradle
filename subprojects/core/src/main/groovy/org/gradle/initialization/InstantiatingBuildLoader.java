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

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
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
     * ready for the projects to be evaluated.
     */
    public void load(ProjectDescriptor rootProjectDescriptor, GradleInternal gradle, ClassLoaderScope classLoaderScope) {
        createProjects(rootProjectDescriptor, gradle, classLoaderScope);
        attachDefaultProject(gradle);
    }

    private void attachDefaultProject(GradleInternal gradle) {
        ProjectSpec spec = ProjectSpecs.forStartParameter(gradle.getStartParameter());
        try {
            gradle.setDefaultProject(spec.selectProject(gradle.getRootProject().getProjectRegistry()));
        } catch (InvalidUserDataException e) {
            throw new GradleException(String.format("Could not select the default project for this build. %s",
                    e.getMessage()), e);
        }
    }

    private void createProjects(ProjectDescriptor rootProjectDescriptor, GradleInternal gradle, ClassLoaderScope classLoaderScope) {
        ProjectInternal rootProject = projectFactory.createProject(rootProjectDescriptor, null, gradle, classLoaderScope);
        gradle.setRootProject(rootProject);
        addProjects(rootProject, rootProjectDescriptor, gradle);
    }

    private void addProjects(ProjectInternal parent, ProjectDescriptor parentProjectDescriptor, GradleInternal gradle) {
        for (ProjectDescriptor childProjectDescriptor : parentProjectDescriptor.getChildren()) {
            ProjectInternal childProject = projectFactory.createProject(childProjectDescriptor, parent, gradle, parent.getClassLoaderScope().createChild());
            addProjects(childProject, childProjectDescriptor, gradle);
        }
    }
}
