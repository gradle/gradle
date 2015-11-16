/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.model.internal.registry.ModelRegistry;

public class CurrentProjectModelResolver implements ProjectModelResolver {
    private final String projectPath;
    private final ProjectModelResolver delegate;

    public CurrentProjectModelResolver(String projectPath, ProjectModelResolver delegate) {
        this.projectPath = projectPath;
        this.delegate = delegate;
    }

    @Override
    public ModelRegistry resolveProjectModel(String path) {
        if (path == null || path.length() == 0) {
            return delegate.resolveProjectModel(projectPath);
        }

        return delegate.resolveProjectModel(path);
    }
}
