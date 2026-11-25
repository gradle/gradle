/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.process.internal;

import com.google.common.collect.Maps;
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.internal.provider.MapPropertyInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the environment of {@link DefaultProcessForkOptions} is kept: a map that starts as the inherited environment,
 * or a lazy property once a task moves it there, see {@link OptionStorage}.
 */
@NullMarked
abstract class EnvironmentStorage {

    abstract Map<String, Object> get();

    abstract void set(Map<String, ?> values);

    abstract void put(String name, @Nullable Object value);

    abstract void putAll(Map<String, ?> values);

    /**
     * Stores the environment in the given property from now on.
     * The current environment, or else the inherited environment read when the property is read, becomes both the convention and the value.
     */
    abstract EnvironmentStorage movedTo(MapProperty<String, Object> property);

    static EnvironmentStorage inField(DefaultProcessForkOptions owner) {
        return new FieldStorage(owner);
    }

    private static final class FieldStorage extends EnvironmentStorage {
        // The inherited environment is read from the owner on first use, as subclasses narrow it
        private final DefaultProcessForkOptions owner;
        @Nullable
        private Map<String, Object> values;

        FieldStorage(DefaultProcessForkOptions owner) {
            this.owner = owner;
        }

        @Override
        Map<String, Object> get() {
            Map<String, Object> current = values;
            if (current == null) {
                current = Maps.newHashMap(owner.getInheritableEnvironment());
                values = current;
            }
            return current;
        }

        @Override
        void set(Map<String, ?> values) {
            this.values = Maps.newHashMap(values);
        }

        @Override
        void put(String name, @Nullable Object value) {
            get().put(name, value);
        }

        @Override
        void putAll(Map<String, ?> values) {
            get().putAll(values);
        }

        @Override
        EnvironmentStorage movedTo(MapProperty<String, Object> property) {
            Map<String, Object> current = values;
            Provider<Map<String, Object>> initialEnvironment = current != null
                ? Providers.of(new HashMap<>(current))
                : Providers.changing(() -> new HashMap<>(owner.getInheritableEnvironment()));
            property.convention(initialEnvironment);
            // Also the value: put() on a property that has only a convention drops the convention, and so the inherited variables
            property.set(initialEnvironment);
            return new PropertyStorage(property);
        }
    }

    // Changes the environment without reading it, so values bound to task outputs are not queried.
    // A lazy map cannot hold null; the process gets the same text, see DefaultProcessForkOptions.getActualEnvironment()
    private static final class PropertyStorage extends EnvironmentStorage {
        private final MapProperty<String, Object> property;

        PropertyStorage(MapProperty<String, Object> property) {
            this.property = property;
        }

        @Override
        Map<String, Object> get() {
            return EagerPropertySupport.conventionKeepingView(property);
        }

        @Override
        void set(Map<String, ?> values) {
            property.set(withoutNullValues(values));
        }

        @Override
        void put(String name, @Nullable Object value) {
            Cast.<MapPropertyInternal<String, Object>>uncheckedNonnullCast(property).insert(name, nonNullValue(value));
        }

        @Override
        void putAll(Map<String, ?> values) {
            Cast.<MapPropertyInternal<String, Object>>uncheckedNonnullCast(property).insertAll(withoutNullValues(values));
        }

        @Override
        EnvironmentStorage movedTo(MapProperty<String, Object> property) {
            throw new IllegalStateException("The environment is already stored in a property.");
        }

        private static Object nonNullValue(@Nullable Object value) {
            return value == null ? "null" : value;
        }

        private static Map<String, Object> withoutNullValues(Map<String, ?> values) {
            Map<String, Object> result = new LinkedHashMap<>();
            values.forEach((name, value) -> result.put(name, nonNullValue(value)));
            return result;
        }
    }
}
