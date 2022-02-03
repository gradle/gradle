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

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;

/**
 * Isolates a value serialized using Java Serialization and is a snapshot for that value.
 */
public class IsolatedJavaSerializedValueSnapshot extends JavaSerializedValueSnapshot implements Isolatable<Object> {
    private final Class<?> originalClass;

    public IsolatedJavaSerializedValueSnapshot(@Nullable HashCode implementationHash, byte[] serializedValue, Class<?> originalClass) {
        super(implementationHash, serializedValue);
        this.originalClass = originalClass;
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return new JavaSerializedValueSnapshot(getImplementationHash(), getValue());
    }

    @Override
    public Object isolate() {
        return populateClass(originalClass);
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isAssignableFrom(originalClass)) {
            return type.cast(isolate());
        }
        if (type.getName().equals(originalClass.getName())) {
            return type.cast(populateClass(type));
        }
        return null;
    }

    public Class<?> getOriginalClass() {
        return originalClass;
    }
}
