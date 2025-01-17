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
package org.gradle.plugin.devel.tasks.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.gradle.api.problems.ProblemDefinition;
import org.gradle.api.problems.internal.DefaultProblemDefinition;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

/**
 * Defines the Gson serialization and deserialization for {@link ProblemDefinition} based on the assumption that they have exactly one implementation.
 */
public final class ProblemReportAdapterFactory implements TypeAdapterFactory {

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, @Nullable TypeToken<T> type) {
        if (type == null) {
            return null;
        }
        Class<?> rawType = type.getRawType();
        if (ProblemDefinition.class.equals(rawType)) {
            return (TypeAdapter<T>) new SingleImplTypeAdapter<>(
                ProblemDefinition.class,
                DefaultProblemDefinition.class,
                gson.getAdapter(JsonElement.class),
                gson.getDelegateAdapter(this, TypeToken.get(DefaultProblemDefinition.class))).nullSafe();
        } else {
            return null;
        }
    }

    private static class SingleImplTypeAdapter<T, U> extends TypeAdapter<T> {

        private final Class<T> baseClass;

        private final Class<U> implClass;

        private final String label;

        private final TypeAdapter<JsonElement> jsonElementAdapter;

        private final TypeAdapter<U> implClassAdapter;

        private SingleImplTypeAdapter(Class<T> baseClass, Class<U> implClass, TypeAdapter<JsonElement> jsonElementAdapter, TypeAdapter<U> implClassAdapter) {
            this.baseClass = baseClass;
            this.implClass = implClass;
            this.label = baseClass.getSimpleName();
            this.jsonElementAdapter = jsonElementAdapter;
            this.implClassAdapter = implClassAdapter;
            if (!baseClass.isAssignableFrom(implClass)) {
                throw new JsonParseException(implClass + " is not a subclass of " + baseClass);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T read(JsonReader in) throws IOException {
            JsonElement jsonElement = jsonElementAdapter.read(in);
            return (T) implClassAdapter.fromJsonTree(jsonElement);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void write(JsonWriter out, T value) throws IOException {
            if (!implClass.isInstance(value)) {
                throw new JsonParseException("Unknown concrete type for " + baseClass + ". Expected: " + implClass + ", actual: " + value.getClass());
            }
            JsonObject jsonObject = implClassAdapter.toJsonTree((U) value).getAsJsonObject();
            JsonObject clone = new JsonObject();
            clone.add(label, new JsonPrimitive(label));
            for (Map.Entry<String, JsonElement> e : jsonObject.entrySet()) {
                clone.add(e.getKey(), e.getValue());
            }
            jsonElementAdapter.write(out, clone);
        }
    }
}
