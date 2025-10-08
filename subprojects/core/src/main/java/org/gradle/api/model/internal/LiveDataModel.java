/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.model.internal;

import org.gradle.internal.isolation.Isolatable;
import org.jspecify.annotations.Nullable;

/**
 * A data model backed by a live object.
 *
 * @param <T> The type of the model.
 */
public class LiveDataModel<T> implements DataModel {

    private final Class<T> type;
    private final Isolatable<T> data;

    public LiveDataModel(Class<T> type, Isolatable<T> data) {
        this.type = type;
        this.data = data;
    }

    @Override
    public String getName() {
        return type.getName();
    }

    public Class<T> getType() {
        return type;
    }

    public Isolatable<T> getData() {
        return data;
    }

    public @Nullable T getIsolatedData() {
        return data.isolate();
    }

    @Override
    public <E> E hydrate(Class<E> type) {
        if (!type.isAssignableFrom(this.type)) {
            // TODO: Implement "fuzzy matching"
            throw new IllegalArgumentException("Cannot hydrate to type " + type.getName() + " from " + this.type.getName());
        }

        T value = getIsolatedData();
        if (value == null) {
            throw new IllegalStateException("Cannot hydrate to type '" + type.getName() + "' since the underlying data is null");
        }
        return type.cast(value);
    }

}
