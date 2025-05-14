/*
 * Copyright 2021 the original author or authors.
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
import org.jspecify.annotations.Nullable;

/**
 * A scope that allows {@link #getModel(String, ToolingModelParameterCarrier) building models}
 * for a given {@link #getTarget() target}.
 */
public interface ToolingModelScope {

    /**
     * A project target for model builder, which is null when the model is build-scoped.
     */
    @Nullable
    ProjectState getTarget();

    /**
     * Creates a model with a given parameter.
     * <p>
     * Can configure the target build or project to locate the corresponding model builder.
     *
     * @return the created model (null is a valid model)
     */
    @Nullable
    Object getModel(String modelName, @Nullable ToolingModelParameterCarrier parameter);
}
