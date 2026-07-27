/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.model.ModelContainer;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

/**
 * The domain object context modeling a {@link org.gradle.api.Project}.
 */
public class ProjectDomainObjectContext implements DomainObjectContext {

    private final ProjectState projectState;

    public ProjectDomainObjectContext(
        ProjectState projectState
    ) {
        this.projectState = projectState;
    }

    @Override
    public @Nullable Path getIdentityPath() {
        return projectState.getIdentity().getBuildTreePath();
    }

    @Override
    public ProjectIdentity getProjectIdentity() {
        return projectState.getIdentity();
    }

    @Override
    public ProjectState getProjectState() {
        return projectState;
    }

    @Override
    public ModelContainer<ProjectInternal> getModel() {
        return projectState;
    }

    @Override
    public Path getBuildPath() {
        return projectState.getIdentity().getBuildPath();
    }

    @Override
    public boolean isScript() {
        return false;
    }

    @Override
    public boolean isRootScript() {
        return false;
    }

    @Override
    public boolean isPluginContext() {
        return false;
    }

    @Override
    public String getDisplayName() {
        return projectState.getIdentity().getDisplayName();
    }

}
