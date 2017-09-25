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

package org.gradle.api.internal.changedetection.state.isolation;

import org.gradle.api.internal.changedetection.state.SerializedValueSnapshot;
import org.gradle.internal.Cast;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;

/**
 * Isolates a Serialized value and is a snapshot for that value.
 */
public class IsolatableSerializedValueSnapshot extends SerializedValueSnapshot implements Isolatable<Object> {
    private final Class<?> originalClass;

    public IsolatableSerializedValueSnapshot(HashCode implementationHash, byte[] serializedValue, Class<?> originalClass) {
        super(implementationHash, serializedValue);
        this.originalClass = originalClass;
    }

    @Override
    public Object isolate() {
        return populateClass(originalClass);
    }

    @Nullable
    @Override
    public <S> Isolatable<S> coerce(Class<S> type) {
        if (type.isAssignableFrom(originalClass)) {
            return Cast.uncheckedCast(this);
        }
        if (type.getName().equals(originalClass.getName())) {
            return Cast.uncheckedCast(new IsolatableSerializedValueSnapshot(getImplementationHash(), getValue(), type));
        }
        return null;
    }
}
