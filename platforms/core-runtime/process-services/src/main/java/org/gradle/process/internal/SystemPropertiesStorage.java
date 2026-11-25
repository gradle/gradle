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

import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

import static org.gradle.process.internal.JvmOptions.FILE_ENCODING_KEY;

/**
 * Where {@link JvmOptions} keeps its system properties, see {@link OptionStorage}.
 *
 * <p>The immutable properties always stay in the map of the options. A task moves the mutable properties
 * and the file encoding, which is one of the immutable properties, to lazy properties.</p>
 */
@NullMarked
abstract class SystemPropertiesStorage {

    final Map<String, Object> immutable;

    private SystemPropertiesStorage(Map<String, Object> immutable) {
        this.immutable = immutable;
    }

    abstract Map<String, Object> getMutable();

    abstract void putMutable(String name, @Nullable Object value);

    abstract Map<String, Object> getImmutable();

    abstract void putImmutable(String name, @Nullable Object value);

    abstract String getEncoding();

    /**
     * Stores the mutable properties and the file encoding in the given properties from now on. Their current values become the conventions.
     */
    abstract SystemPropertiesStorage movedTo(MapProperty<String, Object> mutable, Property<String> encoding);

    static SystemPropertiesStorage inFields(Map<String, Object> immutable) {
        return new FieldStorage(immutable);
    }

    private static final class FieldStorage extends SystemPropertiesStorage {
        private final Map<String, Object> mutable = new TreeMap<>();

        FieldStorage(Map<String, Object> immutable) {
            super(immutable);
        }

        @Override
        Map<String, Object> getMutable() {
            return mutable;
        }

        @Override
        void putMutable(String name, @Nullable Object value) {
            mutable.put(name, value);
        }

        @Override
        Map<String, Object> getImmutable() {
            return immutable;
        }

        @Override
        void putImmutable(String name, @Nullable Object value) {
            immutable.put(name, value);
        }

        @Override
        String getEncoding() {
            return immutable.get(FILE_ENCODING_KEY).toString();
        }

        @Override
        SystemPropertiesStorage movedTo(MapProperty<String, Object> mutable, Property<String> encoding) {
            mutable.convention(new TreeMap<>(this.mutable));
            encoding.convention(getEncoding());
            return new PropertyStorage(immutable, mutable, encoding);
        }
    }

    private static final class PropertyStorage extends SystemPropertiesStorage {
        private final MapProperty<String, Object> mutable;
        private final Property<String> encoding;

        PropertyStorage(Map<String, Object> immutable, MapProperty<String, Object> mutable, Property<String> encoding) {
            super(immutable);
            this.mutable = mutable;
            this.encoding = encoding;
        }

        @Override
        Map<String, Object> getMutable() {
            return EagerPropertySupport.conventionKeepingView(mutable);
        }

        // A lazy map cannot hold null; an empty value is written to the command line the same way
        @Override
        void putMutable(String name, @Nullable Object value) {
            getMutable().put(name, value == null ? "" : value);
        }

        @Override
        Map<String, Object> getImmutable() {
            Map<String, Object> properties = new TreeMap<>(immutable);
            properties.put(FILE_ENCODING_KEY, encoding.get());
            return properties;
        }

        @Override
        void putImmutable(String name, @Nullable Object value) {
            if (FILE_ENCODING_KEY.equals(name)) {
                encoding.set(String.valueOf(value));
            } else {
                immutable.put(name, value);
            }
        }

        @Override
        String getEncoding() {
            return encoding.get();
        }

        @Override
        SystemPropertiesStorage movedTo(MapProperty<String, Object> mutable, Property<String> encoding) {
            throw new IllegalStateException("The system properties are already stored in properties.");
        }
    }
}
