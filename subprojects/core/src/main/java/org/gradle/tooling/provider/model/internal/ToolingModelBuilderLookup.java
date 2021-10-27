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

import org.gradle.api.internal.project.ProjectState;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.UnknownModelException;

import javax.annotation.Nullable;

@ServiceScope(Scopes.Build.class)
public interface ToolingModelBuilderLookup {
    @Nullable
    Registration find(String modelName);

    /**
     * Locates a builder for a project-scoped model.
     */
    Builder locateForClientOperation(String modelName, boolean parameter, ProjectState target) throws UnknownModelException;

    @Nullable
    Builder maybeLocateForBuildScope(String modelName, boolean parameter, BuildState target);

    interface Registration {
        ToolingModelBuilder getBuilder();

        @Nullable
        UserCodeApplicationContext.Application getRegisteredBy();
    }

    interface Builder {
        @Nullable
        Class<?> getParameterType();

        Object build(@Nullable Object parameter);
    }
}
