/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.UnknownModelException;

import javax.annotation.Nullable;

@ServiceScope(Scopes.Build.class)
public interface ToolingModelBuilderLookup {
    @Nullable
    ToolingModelBuilder find(String modelName);

    /**
     * Locates a builder for a build-scoped model.
     */
    Builder locateForClientOperation(String modelName, boolean parameter, GradleInternal target) throws UnknownModelException;

    /**
     * Locates a builder for a project-scoped model.
     */
    Builder locateForClientOperation(String modelName, boolean parameter, ProjectInternal target) throws UnknownModelException;

    interface Builder {
        @Nullable
        Class<?> getParameterType();

        Object build(@Nullable Object parameter);
    }
}
