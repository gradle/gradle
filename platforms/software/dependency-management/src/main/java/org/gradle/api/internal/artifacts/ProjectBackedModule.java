/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.project.ProjectInternal;

/**
 * Exposes the dependency management identity of a project.
 *
 * TODO: Once any mutable field on this class is accessed, we should consider that as the project being observed.
 * Just like we do with configurations, we should then prohibit any changes to the project that would affect the identity.
 */
public class ProjectBackedModule implements Module {

    private final ProjectInternal project;

    public ProjectBackedModule(ProjectInternal project) {
        this.project = project;
    }

    @Override
    public String getGroup() {
        return project.getGroup().toString();
    }

    @Override
    public String getName() {
        return project.getName();
    }

    @Override
    public String getVersion() {
        return project.getVersion().toString();
    }

    @Override
    public String getStatus() {
        return project.getStatus().toString();
    }

    @Override
    public ProjectComponentIdentifier getComponentId() {
        return project.getOwner().getComponentIdentifier();
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
