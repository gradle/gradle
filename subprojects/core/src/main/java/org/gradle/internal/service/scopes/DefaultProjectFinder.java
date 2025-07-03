/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.util.Path;

/**
 * Default implementation of {@link ProjectFinder}.
 */
public class DefaultProjectFinder implements ProjectFinder {

    private final ProjectState baseProject;

    public DefaultProjectFinder(ProjectState baseProject) {
        this.baseProject = baseProject;
    }

    @Override
    public Path resolveIdentityPath(String path) {
        Path resolvedProjectPath = baseProject.getIdentity().getProjectPath().absolutePath(Path.path(path));
        return baseProject.getOwner().calculateIdentityPathForProject(resolvedProjectPath);
    }

}
