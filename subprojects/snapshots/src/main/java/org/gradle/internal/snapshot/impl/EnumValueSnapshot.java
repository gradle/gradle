/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.hash.Hasher;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

public class EnumValueSnapshot implements ValueSnapshot {
    private final String className;
    private final String name;

    public EnumValueSnapshot(Enum<?> value) {
        // Don't retain the value, to allow ClassLoader to be collected
        this.className = value.getClass().getName();
        this.name = value.name();
    }

    public EnumValueSnapshot(String className, String name) {
        this.className = className;
        this.name = name;
    }

    public String getClassName() {
        return className;
    }

    public String getName() {
        return name;
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        if (isEqualEnum(value)) {
            return this;
        }
        return snapshotter.snapshot(value);
    }

    private boolean isEqualEnum(Object value) {
        if (value instanceof Enum) {
            Enum<?> enumValue = (Enum<?>) value;
            if (enumValue.name().equals(name) && enumValue.getClass().getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString(className);
        hasher.putString(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        EnumValueSnapshot other = (EnumValueSnapshot) obj;
        return className.equals(other.className) && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return className.hashCode() ^ name.hashCode();
    }
}
