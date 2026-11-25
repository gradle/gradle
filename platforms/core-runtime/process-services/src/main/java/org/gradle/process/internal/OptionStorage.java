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

import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Where an eager option keeps its value.
 *
 * <p>An option starts in a plain field. A task that exposes the option as a lazy property moves it there,
 * so the option has one storage at any time and its accessors need no knowledge of which.</p>
 */
@NullMarked
abstract class OptionStorage<T> {

    @Nullable
    abstract T get();

    abstract void set(@Nullable T value);

    T required() {
        return Objects.requireNonNull(get());
    }

    static <T> OptionStorage<T> inField(@Nullable T initialValue) {
        return new FieldStorage<>(initialValue);
    }

    /**
     * Stores the option in the given property from now on. The current value becomes its convention.
     */
    OptionStorage<T> movedTo(Property<T> property) {
        T current = get();
        if (current != null) {
            property.convention(current);
        }
        return new PropertyStorage<>(property);
    }

    /**
     * As {@link #movedTo(Property)}, for an option that accepts any object and reads it as text.
     * An object that is not a string is converted when the property is read, as the field did.
     */
    static OptionStorage<Object> movedToText(OptionStorage<Object> storage, Property<String> property) {
        Object current = storage.get();
        if (current != null) {
            property.convention(new DefaultProvider<>(current::toString));
        }
        return new TextPropertyStorage(property);
    }

    private static final class FieldStorage<T> extends OptionStorage<T> {
        @Nullable
        private T value;

        FieldStorage(@Nullable T value) {
            this.value = value;
        }

        @Override
        @Nullable
        T get() {
            return value;
        }

        @Override
        void set(@Nullable T value) {
            this.value = value;
        }
    }

    private static final class PropertyStorage<T> extends OptionStorage<T> {
        private final Property<T> property;

        PropertyStorage(Property<T> property) {
            this.property = property;
        }

        @Override
        @Nullable
        T get() {
            return property.getOrNull();
        }

        @Override
        void set(@Nullable T value) {
            EagerPropertySupport.setEager(property, value);
        }

        @Override
        T required() {
            return property.get();
        }
    }

    private static final class TextPropertyStorage extends OptionStorage<Object> {
        private final Property<String> property;

        TextPropertyStorage(Property<String> property) {
            this.property = property;
        }

        @Override
        @Nullable
        Object get() {
            return property.getOrNull();
        }

        @Override
        void set(@Nullable Object value) {
            if (value == null || value instanceof String) {
                EagerPropertySupport.setEager(property, (String) value);
            } else {
                property.set(new DefaultProvider<>(value::toString));
            }
        }
    }
}
