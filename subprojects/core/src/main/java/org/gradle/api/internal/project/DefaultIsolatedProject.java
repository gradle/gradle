/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.project.IsolatedProject;
import org.gradle.api.file.Directory;

public final class DefaultIsolatedProject implements IsolatedProject {

    private final ProjectInternal project;
    private final ProjectInternal rootProject;

    public DefaultIsolatedProject(ProjectInternal project, ProjectInternal rootProject) {
        this.project = project;
        this.rootProject = rootProject;
    }

    @Override
    public String getName() {
        return project.getName();
    }

    @Override
    public String getPath() {
        return project.getPath();
    }

    @Override
    public String getBuildTreePath() {
        return project.getBuildTreePath();
    }

    @Override
    public Directory getProjectDirectory() {
        return project.getLayout().getProjectDirectory();
    }

    @Override
    public IsolatedProject getRootProject() {
        return project.equals(rootProject)
            ? this
            : rootProject.getIsolated();
    }

    @Override
    public int hashCode() {
        return project.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DefaultIsolatedProject)) {
            return false;
        }
        DefaultIsolatedProject that = (DefaultIsolatedProject) obj;
        return this.project.equals(that.project);
    }

    @Override
    public String toString() {
        return "DefaultIsolatedProject{" + project + '}';
    }
}
