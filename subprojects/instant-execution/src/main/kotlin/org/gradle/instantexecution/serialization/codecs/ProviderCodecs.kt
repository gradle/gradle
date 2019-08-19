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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.DefaultListProperty
import org.gradle.api.internal.provider.DefaultMapProperty
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import java.io.File


private
suspend fun WriteContext.writeProvider(value: Provider<*>) {
    write(unpack(value))
}


private
suspend fun ReadContext.readProvider(): Provider<Any> {
    val value = read()
    return when (value) {
        is BrokenValue -> DefaultProvider { value.rethrow() }
        else -> Providers.ofNullable(value)
    }
}


private
fun unpack(value: Provider<*>): Any? {
    try {
        return value.orNull
    } catch (e: Exception) {
        return BrokenValue(e)
    }
}


object
ProviderCodec : Codec<Provider<*>> {
    override suspend fun WriteContext.encode(value: Provider<*>) {
        // TODO - should write the provider value type
        writeProvider(value)
    }

    override suspend fun ReadContext.decode() = readProvider()
}


object
PropertyCodec : Codec<Property<*>> {
    override suspend fun WriteContext.encode(value: Property<*>) {
        // TODO - should write the property type
        writeProvider(value)
    }

    override suspend fun ReadContext.decode(): Property<*>? {
        val value = readProvider()
        return DefaultProperty(Any::class.java).provider(value)
    }
}


class
DirectoryPropertyCodec(private val filePropertyFactory: FilePropertyFactory) : Codec<DirectoryProperty> {
    override suspend fun WriteContext.encode(value: DirectoryProperty) {
        writeProvider(value.asFile)
    }

    override suspend fun ReadContext.decode(): DirectoryProperty? {
        val value = readProvider() as Provider<File>
        return filePropertyFactory.newDirectoryProperty().fileProvider(value)
    }
}


class
RegularFilePropertyCodec(private val filePropertyFactory: FilePropertyFactory) : Codec<RegularFileProperty> {
    override suspend fun WriteContext.encode(value: RegularFileProperty) {
        writeProvider(value.asFile)
    }

    override suspend fun ReadContext.decode(): RegularFileProperty? {
        val value = readProvider() as Provider<File>
        return filePropertyFactory.newFileProperty().fileProvider(value)
    }
}


object
ListPropertyCodec : Codec<ListProperty<*>> {
    override suspend fun WriteContext.encode(value: ListProperty<*>) {
        // TODO - should write the element type
        writeProvider(value)
    }

    override suspend fun ReadContext.decode(): ListProperty<*>? {
        val value = readProvider() as Provider<List<Any>>
        return DefaultListProperty(Any::class.java).value(value)
    }
}


object
MapPropertyCodec : Codec<MapProperty<*, *>> {
    override suspend fun WriteContext.encode(value: MapProperty<*, *>) {
        // TODO - should write the key and value types
        writeProvider(value)
    }

    override suspend fun ReadContext.decode(): MapProperty<*, *>? {
        val value = readProvider() as Provider<Map<Any, Any>>
        return DefaultMapProperty(Any::class.java, Any::class.java).value(value)
    }
}
