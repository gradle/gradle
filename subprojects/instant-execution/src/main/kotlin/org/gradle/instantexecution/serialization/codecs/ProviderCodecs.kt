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

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultDirectoryVar
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultRegularFileVar
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.AbstractMappingProvider
import org.gradle.api.internal.provider.DefaultListProperty
import org.gradle.api.internal.provider.DefaultMapProperty
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.DefaultSetProperty
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.writeCollection


private
suspend fun WriteContext.writeProvider(value: ProviderInternal<*>) {
    if (value.isValueProducedByTask && value is AbstractMappingProvider<*, *>) {
        // Need to serialize the transformation and its source, as the value is not available until execution time
        writeBoolean(true)
        BeanCodec().run { encode(value) }
    } else {
        // Can serialize the value and discard the provider
        writeBoolean(false)
        write(unpack(value))
    }
}


private
suspend fun ReadContext.readProvider(): ProviderInternal<Any> =
    if (readBoolean()) {
        BeanCodec().run { decode() }!!.uncheckedCast()
    } else {
        when (val value = read()) {
            is BrokenValue -> DefaultProvider<Any> { value.rethrow() }.uncheckedCast()
            else -> Providers.ofNullable(value)
        }
    }


private
fun unpack(value: Provider<*>): Any? =
    try {
        value.orNull
    } catch (e: Exception) {
        BrokenValue(e)
    }


object
ProviderCodec : Codec<ProviderInternal<*>> {
    override suspend fun WriteContext.encode(value: ProviderInternal<*>) {
        // TODO - should write the provider value type
        writeProvider(value)
    }

    override suspend fun ReadContext.decode() = readProvider()
}


object
PropertyCodec : Codec<DefaultProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultProperty<*>) {
        // TODO - should write the property type
        writeProvider(value.provider)
    }

    override suspend fun ReadContext.decode(): DefaultProperty<*> {
        val provider = readProvider()
        return DefaultProperty(Any::class.java).provider(provider)
    }
}


class
DirectoryPropertyCodec(private val filePropertyFactory: FilePropertyFactory) : Codec<DefaultDirectoryVar> {
    override suspend fun WriteContext.encode(value: DefaultDirectoryVar) {
        writeProvider(value.provider)
    }

    override suspend fun ReadContext.decode(): DefaultDirectoryVar {
        val provider: Provider<Directory> = readProvider().uncheckedCast()
        return filePropertyFactory.newDirectoryProperty().value(provider) as DefaultDirectoryVar
    }
}


class
RegularFilePropertyCodec(private val filePropertyFactory: FilePropertyFactory) : Codec<DefaultRegularFileVar> {
    override suspend fun WriteContext.encode(value: DefaultRegularFileVar) {
        writeProvider(value.provider)
    }

    override suspend fun ReadContext.decode(): DefaultRegularFileVar {
        val provider: Provider<RegularFile> = readProvider().uncheckedCast()
        return filePropertyFactory.newFileProperty().value(provider) as DefaultRegularFileVar
    }
}


object
ListPropertyCodec : Codec<DefaultListProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultListProperty<*>) {
        // TODO - should write the element type
        writeCollection(value.providers) { writeProvider(it) }
    }

    override suspend fun ReadContext.decode(): DefaultListProperty<*> {
        val providers = readList { readProvider() }
        return DefaultListProperty(Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}


object
SetPropertyCodec : Codec<DefaultSetProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultSetProperty<*>) {
        // TODO - should write the element type
        writeCollection(value.providers) { writeProvider(it) }
    }

    override suspend fun ReadContext.decode(): DefaultSetProperty<*> {
        val providers = readList { readProvider() }
        return DefaultSetProperty(Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}


object
MapPropertyCodec : Codec<DefaultMapProperty<*, *>> {
    override suspend fun WriteContext.encode(value: DefaultMapProperty<*, *>) {
        // TODO - should write the key and value types
        writeCollection(value.providers) { writeProvider(it) }
    }

    override suspend fun ReadContext.decode(): DefaultMapProperty<*, *> {
        val providers = readList { readProvider() }
        return DefaultMapProperty(Any::class.java, Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}
