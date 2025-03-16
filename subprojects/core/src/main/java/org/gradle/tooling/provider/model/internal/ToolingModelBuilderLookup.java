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
import org.gradle.internal.build.BuildState;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.jspecify.annotations.Nullable;

@ServiceScope(Scope.Build.class)
public interface ToolingModelBuilderLookup {
    @Nullable
    Registration find(String modelName);

    /**
     * Locates a builder for a project-scoped model.
     */
    Builder locateForClientOperation(String modelName, boolean parameter, ProjectState target) throws UnknownModelException;

    /**
     * Locates a builder for a build-scoped model.
     */
    @Nullable
    Builder maybeLocateForBuildScope(String modelName, boolean parameter, BuildState target);

    interface Registration {
        ToolingModelBuilder getBuilder();

        UserCodeApplicationContext.@Nullable Application getRegisteredBy();
    }

    interface Builder {
        @Nullable
        Class<?> getParameterType();

        Object build(@Nullable Object parameter);
    }
}
