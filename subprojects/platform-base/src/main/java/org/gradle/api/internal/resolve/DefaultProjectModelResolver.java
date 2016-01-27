/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.resolve;

import org.gradle.api.UnknownProjectException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.model.internal.registry.ModelRegistry;

public class DefaultProjectModelResolver implements ProjectModelResolver {
    private final ProjectRegistry<ProjectInternal> delegate;

    public DefaultProjectModelResolver(ProjectRegistry<ProjectInternal> delegate) {
        this.delegate = delegate;
    }

    @Override
    public ModelRegistry resolveProjectModel(String path) {
        ProjectInternal projectInternal = delegate.getProject(path);
        if (projectInternal == null) {
            throw new UnknownProjectException("Project with path '" + path + "' not found.");
        }

        // TODO This is a brain-dead way to ensure that the reference project's model is ready to access
        projectInternal.evaluate();
        projectInternal.getTasks().discoverTasks();
        return projectInternal.getModelRegistry();
    }
}
