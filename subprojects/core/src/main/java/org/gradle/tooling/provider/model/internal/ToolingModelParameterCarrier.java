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
import org.gradle.internal.hash.HashCode;

/**
 * Container of the parameter value for a model builder.
 */
@NonNullApi
public class ToolingModelParameterCarrier {

    private final Object parameter;
    private final ToolingModelParameterHasher hasher;
    private final ToolingModelParameterAdapter adapter;

    public ToolingModelParameterCarrier(Object parameter, ToolingModelParameterHasher hasher, ToolingModelParameterAdapter adapter) {
        this.parameter = parameter;
        this.hasher = hasher;
        this.adapter = adapter;
    }

    public Object getValue(Class<?> expectedParameterType) {
        return adapter.apply(expectedParameterType, parameter);
    }

    public HashCode getHash() {
        return hasher.hash(parameter);
    }
}
