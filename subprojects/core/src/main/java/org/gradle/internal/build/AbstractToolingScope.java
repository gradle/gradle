/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;

import javax.annotation.Nullable;
import java.util.Objects;

public abstract class AbstractToolingScope implements ToolingModelScope {
    protected abstract ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException;

    @Override
    public Object getModel(String modelName, @Nullable ToolingModelParameterCarrier parameter) {
        ToolingModelBuilderLookup.Builder builder = locateBuilder();
        if (parameter == null) {
            return builder.build(null);
        } else {
            Class<?> expectedParameterType = Objects.requireNonNull(builder.getParameterType(), "Expected builder with parameter support");
            Object parameterValue = parameter.getView(expectedParameterType);
            return builder.build(parameterValue);
        }
    }
}
