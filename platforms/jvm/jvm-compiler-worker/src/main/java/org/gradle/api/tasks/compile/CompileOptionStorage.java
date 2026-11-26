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

package org.gradle.api.tasks.compile;

import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * Where an eager compile option keeps its value.
 *
 * <p>An option starts in a plain field. When the options belong to a compile task, the task moves the option
 * to a lazy property of its lazy tree, so the option has one storage at any time.</p>
 *
 * <p>Only the field storage can be serialized. The copies of the options that are sent to a compiler worker
 * are created with {@code new}, so they always store in fields.</p>
 */
@NullMarked
abstract class CompileOptionStorage<T> implements Serializable {

    @Nullable
    abstract T get();

    abstract void set(@Nullable T value);

    static <T> CompileOptionStorage<T> inField(@Nullable T initialValue) {
        return new FieldStorage<>(initialValue);
    }

    /**
     * Stores the option in the given property from now on. The current value becomes its convention.
     */
    CompileOptionStorage<T> movedTo(Property<T> property) {
        T current = get();
        if (current != null) {
            property.convention(current);
        }
        return new PropertyStorage<>(property);
    }

    /**
     * As {@link #movedTo(Property)}, for a list option. The getter returns a live view of the property,
     * or null when the option was set to null.
     */
    static CompileOptionStorage<List<String>> movedTo(CompileOptionStorage<List<String>> storage, ListProperty<String> property) {
        List<String> current = storage.get();
        if (current == null) {
            EagerPropertySupport.setEager(property, null);
        } else if (!current.isEmpty()) {
            property.convention(current);
        }
        return new ListPropertyStorage(property);
    }

    private static final class FieldStorage<T> extends CompileOptionStorage<T> {
        private static final long serialVersionUID = 0;

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

    private static final class PropertyStorage<T> extends CompileOptionStorage<T> {
        private static final long serialVersionUID = 0;

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
    }

    private static final class ListPropertyStorage extends CompileOptionStorage<List<String>> {
        private static final long serialVersionUID = 0;

        private final ListProperty<String> property;

        ListPropertyStorage(ListProperty<String> property) {
            this.property = property;
        }

        @Override
        @Nullable
        List<String> get() {
            return property.isPresent() ? EagerPropertySupport.conventionKeepingView(property) : null;
        }

        @Override
        void set(@Nullable List<String> value) {
            EagerPropertySupport.setEager(property, value);
        }
    }
}
