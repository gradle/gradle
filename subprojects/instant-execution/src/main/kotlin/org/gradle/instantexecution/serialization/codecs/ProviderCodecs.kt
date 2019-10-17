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
import org.gradle.api.internal.provider.SystemPropertyProvider
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.westline.WestlineService
import org.gradle.api.westline.WestlineServiceParameters
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.decodePreservingIdentity
import org.gradle.instantexecution.serialization.encodePreservingIdentityOf
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.instantexecution.westline.WestlineServiceProvider
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.workers.internal.IsolatableSerializerRegistry


class
ProviderCodec(
    private val isolatableSerializerRegistry: IsolatableSerializerRegistry,
    private val instantiatorFactory: InstantiatorFactory,
    private val listenerManager: ListenerManager,
    private val providers: ProviderFactory
) : Codec<ProviderInternal<*>> {
    suspend fun WriteContext.writeProvider(value: ProviderInternal<*>) {
        when {
            value is SystemPropertyProvider -> {
                writeByte(4)
                writeString(value.propertyName)
            }
            value is WestlineServiceProvider<*, *> -> {
                writeByte(1)
                encodePreservingIdentityOf(sharedIdentities, value) {
                    writeClass(value.serviceType)
                    // TODO - This can be inferred from the service type, so does not need to be serialized
                    writeClass(value.parametersType)
                    encodeIsolatable(value.parameters, isolatableSerializerRegistry)
                }
            }
            value.isValueProducedByTask && value is AbstractMappingProvider<*, *> -> {
                // Need to serialize the transformation and its source, as the value is not available until execution time
                writeByte(2)
                BeanCodec().run { encode(value) }
            }
            else -> {
                // Can serialize the value and discard the provider
                writeByte(3)
                write(unpack(value))
            }
        }
    }


    suspend fun ReadContext.readProvider(): ProviderInternal<Any> =
        when (readByte()) {
            1.toByte() -> {
                decodePreservingIdentity(sharedIdentities) { id ->
                    val serviceType = readClass() as Class<WestlineService<WestlineServiceParameters>>
                    val parametersType = readClass() as Class<WestlineServiceParameters>
                    val parameters = decodeIsolatable<WestlineServiceParameters>(serviceType, isolatableSerializerRegistry)
                    val provider = WestlineServiceProvider(serviceType, parametersType, parameters, instantiatorFactory, listenerManager) as ProviderInternal<Any>
                    sharedIdentities.putInstance(id, provider)
                    provider
                }
            }
            2.toByte() -> BeanCodec().run { decode() }!!.uncheckedCast()
            3.toByte() -> {
                when (val value = read()) {
                    is BrokenValue -> DefaultProvider<Any> { value.rethrow() }.uncheckedCast()
                    else -> Providers.ofNullable(value)
                }
            }
            4.toByte() -> {
                providers.systemProperty(readString()).uncheckedCast()
            }
            else -> TODO("fail")
        }


    private
    fun unpack(value: Provider<*>): Any? =
        try {
            value.orNull
        } catch (e: Exception) {
            BrokenValue(e)
        }

    override suspend fun WriteContext.encode(value: ProviderInternal<*>) {
        // TODO - should write the provider value type
        writeProvider(value)
    }

    override suspend fun ReadContext.decode() = readProvider()
}


class
PropertyCodec(private val providerCodec: ProviderCodec) : Codec<DefaultProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultProperty<*>) {
        // TODO - should write the property type
        providerCodec.run { writeProvider(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultProperty<*> {
        val provider = providerCodec.run { readProvider() }
        return DefaultProperty(Any::class.java).provider(provider)
    }
}


class
DirectoryPropertyCodec(
    private val filePropertyFactory: FilePropertyFactory,
    private val providerCodec: ProviderCodec
) : Codec<DefaultDirectoryVar> {

    override suspend fun WriteContext.encode(value: DefaultDirectoryVar) {
        providerCodec.run { writeProvider(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultDirectoryVar {
        val provider: Provider<Directory> = providerCodec.run { readProvider().uncheckedCast() }
        return filePropertyFactory.newDirectoryProperty().value(provider) as DefaultDirectoryVar
    }
}


class
RegularFilePropertyCodec(
    private val filePropertyFactory: FilePropertyFactory,
    private val providerCodec: ProviderCodec
) : Codec<DefaultRegularFileVar> {
    override suspend fun WriteContext.encode(value: DefaultRegularFileVar) {
        providerCodec.run { writeProvider(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultRegularFileVar {
        val provider: Provider<RegularFile> = providerCodec.run { readProvider().uncheckedCast() }
        return filePropertyFactory.newFileProperty().value(provider) as DefaultRegularFileVar
    }
}


class
ListPropertyCodec(
    private val providerCodec: ProviderCodec
) : Codec<DefaultListProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultListProperty<*>) {
        // TODO - should write the element type
        writeCollection(value.providers) { providerCodec.run { writeProvider(it) } }
    }

    override suspend fun ReadContext.decode(): DefaultListProperty<*> {
        val providers = readList { providerCodec.run { readProvider() } }
        return DefaultListProperty(Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}


class
SetPropertyCodec(
    private val providerCodec: ProviderCodec
) : Codec<DefaultSetProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultSetProperty<*>) {
        // TODO - should write the element type
        writeCollection(value.providers) { providerCodec.run { writeProvider(it) } }
    }

    override suspend fun ReadContext.decode(): DefaultSetProperty<*> {
        val providers = readList { providerCodec.run { readProvider() } }
        return DefaultSetProperty(Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}


class
MapPropertyCodec(
    private val providerCodec: ProviderCodec
) : Codec<DefaultMapProperty<*, *>> {
    override suspend fun WriteContext.encode(value: DefaultMapProperty<*, *>) {
        // TODO - should write the key and value types
        writeCollection(value.providers) { providerCodec.run { writeProvider(it) } }
    }

    override suspend fun ReadContext.decode(): DefaultMapProperty<*, *> {
        val providers = readList { providerCodec.run { readProvider() } }
        return DefaultMapProperty(Any::class.java, Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}
