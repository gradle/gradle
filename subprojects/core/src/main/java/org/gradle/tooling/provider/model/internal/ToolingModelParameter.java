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
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshotter;

/**
 * Container of the parameter value for a model builder.
 */
@NonNullApi
public class ToolingModelParameter {

    private final Object value;
    private final ValueSnapshotter valueSnapshotter;
    private final ToolingModelParameterAdapter adapter;

    public ToolingModelParameter(Object value, ValueSnapshotter valueSnapshotter, ToolingModelParameterAdapter adapter) {
        this.value = value;
        this.valueSnapshotter = valueSnapshotter;
        this.adapter = adapter;
    }

    public Object getValue(Class<?> expectedParameterType) {
        return adapter.apply(expectedParameterType, value);
    }

    public HashCode getHash() {
        Hasher hasher = Hashing.newHasher();
        hasher.put(valueSnapshotter.snapshot(value));
        return hasher.hash();
    }
}
