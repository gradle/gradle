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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.Named;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.caching.internal.BuildCacheHasher;

public class ManagedNamedTypeSnapshot implements ValueSnapshot {
    private final String className;
    private final String name;

    public ManagedNamedTypeSnapshot(Named value) {
        // Don't retain the value, to allow ClassLoader to be collected
        className = value.getClass().getName();
        name = value.getName();
    }

    public ManagedNamedTypeSnapshot(String className, String name) {
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
        if (value instanceof NamedObjectInstantiator.Managed) {
            Named named = (Named) value;
            if (className.equals(named.getClass().getName()) && name.equals(named.getName())) {
                return this;
            }
        }
        return snapshotter.snapshot(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        ManagedNamedTypeSnapshot other = (ManagedNamedTypeSnapshot) obj;
        return other.className.equals(className) && other.name.equals(name);
    }

    @Override
    public int hashCode() {
        return className.hashCode() ^ name.hashCode();
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString(className);
        hasher.putString(name);
    }
}
