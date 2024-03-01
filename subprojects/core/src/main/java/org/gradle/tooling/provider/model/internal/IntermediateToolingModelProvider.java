/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Fetches models for given projects in an Isolated Project-compatible manner.
 * <p>
 * It should be used by tooling model builders when they need to aggregate models
 * from <b>multiple projects of the same build</b>.
 */
@NonNullApi
@ServiceScope(Scopes.Build.class)
public interface IntermediateToolingModelProvider {

    /**
     * Fetches models of a given type for the given projects passing a parameter to the underlying builder.
     */
    <T> List<T> getModels(Project requester, List<Project> targets, String modelName, Class<T> modelType, @Nullable Object parameter);

    /**
     * Fetches models of a given type for the given projects passing a parameter to the underlying builder.
     * <p>
     * Model name to find the underlying builder is derived from the binary name of the {@code modelType}.
     */
    default <T> List<T> getModels(Project requester, List<Project> targets, Class<T> modelType, @Nullable Object parameter) {
        return getModels(requester, targets, modelType.getName(), modelType, parameter);
    }

    /**
     * Applies a plugin of a given type to the given projects.
     */
    <P extends Plugin<Project>> void applyPlugin(Project requester, List<Project> targets, Class<P> pluginClass);

}
