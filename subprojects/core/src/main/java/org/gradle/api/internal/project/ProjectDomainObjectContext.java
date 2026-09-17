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

package org.gradle.api.internal.project;

import org.gradle.internal.model.DomainObjectContext;
import org.gradle.util.Path;

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

    public ProjectIdentity getIdentity() {
        return projectState.getIdentity();
    }

    @Override
    public ProjectState getModel() {
        return projectState;
    }

    @Override
    public Path getBuildPath() {
        return projectState.getIdentity().getBuildPath();
    }

    @Override
    public String getDisplayName() {
        return projectState.getIdentity().getDisplayName();
    }

}
