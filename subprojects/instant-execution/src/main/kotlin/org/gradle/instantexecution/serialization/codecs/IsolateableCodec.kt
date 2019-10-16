/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.isolation.Isolatable
import org.gradle.workers.internal.IsolatableSerializerRegistry

suspend fun WriteContext.encodeIsolatable(value: Isolatable<*>, isolatableSerializerRegistry: IsolatableSerializerRegistry) {
    isolatableSerializerRegistry.writeIsolatable(this, value)
}

suspend fun <T> ReadContext.decodeIsolatable(implementationClass: Class<*>, isolatableSerializerRegistry: IsolatableSerializerRegistry): Isolatable<T> {
    // TODO - should not need to do anything with the context classloader
    val previousContextClassLoader = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = implementationClass.classLoader
    return try {
        isolatableSerializerRegistry.readIsolatable(this) as Isolatable<T>
    } finally {
        Thread.currentThread().contextClassLoader = previousContextClassLoader
    }
}
