/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.snapshot;

import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.serialize.Serializer;

import java.util.function.Function;

public interface SnapshotSerializerRegistry {

    <T, V extends ValueSnapshot & Isolatable<T>, S extends Serializer<V>>
    void registerIsolatableValueSnapshotSerializer(Class<T> valueType, Class<S> serializerType);

    <T, V extends ValueSnapshot & Isolatable<T>>
    Function<T, V> isolatableValueSnapshotFactoryFor(Class<T> valueType);
}
