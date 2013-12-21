/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.Project;

public class ProjectBackedModule implements ModuleInternal {

    private final Project project;

    public ProjectBackedModule(Project project) {
        this.project = project;
    }

    public String getGroup() {
        return project.getGroup().toString();
    }

    public String getName() {
        return project.getName();
    }

    public String getVersion() {
        return project.getVersion().toString();
    }

    public String getStatus() {
        return project.getStatus().toString();
    }

    public String getProjectPath() {
        return project.getPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ProjectBackedModule that = (ProjectBackedModule) o;

        if (project != null ? !project.equals(that.project) : that.project != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return project != null ? project.hashCode() : 0;
    }
}
