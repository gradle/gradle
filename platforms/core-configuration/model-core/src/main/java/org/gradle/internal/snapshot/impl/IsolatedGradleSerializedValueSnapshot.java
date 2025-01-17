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

package org.gradle.internal.snapshot.impl;

import org.gradle.api.GradleException;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;

/**
 * Isolates a value serialized using Gradle Serialization and is a snapshot for that value.
 */
class IsolatedGradleSerializedValueSnapshot extends GradleSerializedValueSnapshot implements Isolatable<Object> {

    private final byte[] serializedValue;
    private final Serializer<?> serializer;
    private final Class<?> originalClass;
    private final HashCode classLoaderHash;

    public IsolatedGradleSerializedValueSnapshot(@Nullable HashCode classLoaderHash, byte[] serializedValue, Class<?> originalClass, Serializer<?> serializer) {
        super(classLoaderHash, serializedValue);
        this.serializedValue = serializedValue;
        this.serializer = serializer;
        this.originalClass = originalClass;
        this.classLoaderHash = classLoaderHash;
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return new GradleSerializedValueSnapshot(classLoaderHash, serializedValue);
    }

    @Nullable
    @Override
    public Object isolate() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(serializedValue);
        try (KryoBackedDecoder encoder = new KryoBackedDecoder(inputStream)) {
            return serializer.read(encoder);
        } catch (Exception e) {
            throw new GradleException("Failed to decode serialized value", e);
        }
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isAssignableFrom(originalClass)) {
            return type.cast(isolate());
        }
        return null;
    }

}
