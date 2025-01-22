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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.BuildSession.class)
public interface ValueSnapshotterSerializerRegistry extends SerializerRegistry {

    /**
     * Return true if a type of the given class can be isolated by this serializer registry.
     *
     * @param valueClass The type to isolate.
     *
     * @return True if the type can be isolated, false otherwise.
     */
    boolean canIsolate(Class<?> valueClass);

    /**
     * Isolate the given object.
     *
     * @param baseType The object to isolate.
     *
     * @return The isolated object.
     *
     * @param <T> The type of the object.
     */
    <T> Isolatable<T> buildIsolated(T baseType);

}
