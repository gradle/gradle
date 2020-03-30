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
import org.gradle.api.internal.provider.DefaultListProperty
import org.gradle.api.internal.provider.DefaultMapProperty
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.DefaultSetProperty
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory.ValueSourceProvider
import org.gradle.api.internal.provider.FlatMapProvider
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.decodePreservingSharedIdentity
import org.gradle.instantexecution.serialization.encodePreservingSharedIdentityOf
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.writeCollection


/**
 * This is not used directly when encoding or decoding the object graph. This codec takes care of substituting a provider whose
 * value is known at configuration time with a fixed value.
 */
class FixedValueReplacingProviderCodec(valueSourceProviderFactory: ValueSourceProviderFactory, buildServiceRegistry: BuildServiceRegistryInternal) : Codec<ProviderInternal<*>> {
    private
    val providerWithChangingValueCodec = BindingsBackedCodec {
        bind(ValueSourceProviderCodec(valueSourceProviderFactory))
        bind(BuildServiceProviderCodec(buildServiceRegistry))
        bind(BeanCodec())
    }

    override suspend fun WriteContext.encode(value: ProviderInternal<*>) {
        if (value is FlatMapProvider<*, *>) {
            // Replace the provider with its backing provider
            encode(value.backingProvider())
        } else if (value.isValueProducedByTask) {
            // Cannot write a fixed value, so write the provider itself
            writeBoolean(true)
            providerWithChangingValueCodec.run { encode(sourceOf(value)) }
        } else {
            // Can serialize a fixed value and discard the provider
            writeBoolean(false)
            write(unpack(value))
        }
    }

    override suspend fun ReadContext.decode(): ProviderInternal<*>? {
        return if (readBoolean()) {
            providerWithChangingValueCodec.run { decode() }!!.uncheckedCast()
        } else {
            when (val value = read()) {
                is BrokenValue -> DefaultProvider<Any> { value.rethrow() }.uncheckedCast()
                else -> Providers.ofNullable(value)
            }
        }
    }
}


private
fun sourceOf(value: Provider<*>): Provider<*> {
    // Don't serialize simple property instances in a chain of providers, instead replace them with their source provider.
    return if (value is DefaultProperty<*>) {
        sourceOf(value.provider)
    } else {
        value
    }
}


private
fun unpack(value: Provider<*>): Any? =
    try {
        value.orNull
    } catch (e: Exception) {
        BrokenValue(e)
    }


/**
 * Handles Provider instances seen in the object graph, and delegates to another codec that handles the value.
 */
class
ProviderCodec(private val providerCodec: Codec<ProviderInternal<*>>) : Codec<ProviderInternal<*>> {
    override suspend fun WriteContext.encode(value: ProviderInternal<*>) {
        // TODO - should write the provider value type
        providerCodec.run { encode(value) }
    }

    override suspend fun ReadContext.decode() = providerCodec.run { decode() }
}


class
BuildServiceProviderCodec(private val serviceRegistry: BuildServiceRegistryInternal) : Codec<BuildServiceProvider<*, *>> {
    override suspend fun WriteContext.encode(value: BuildServiceProvider<*, *>) {
        encodePreservingSharedIdentityOf(value) {
            writeString(value.getName())
            writeClass(value.getImplementationType())
            write(value.getParameters())
            writeInt(serviceRegistry.forService(value).maxUsages)
        }
    }

    override suspend fun ReadContext.decode(): BuildServiceProvider<*, *>? =
        decodePreservingSharedIdentity {
            val name = readString()
            val implementationType = readClass().uncheckedCast<Class<BuildService<*>>>()
            val parameters = read() as BuildServiceParameters?
            val maxUsages = readInt()
            val provider = serviceRegistry.register(name, implementationType, parameters, maxUsages)
            provider
        }
}


class
ValueSourceProviderCodec(
    private val valueSourceProviderFactory: ValueSourceProviderFactory
) : Codec<ValueSourceProvider<*, *>> {

    override suspend fun WriteContext.encode(value: ValueSourceProvider<*, *>) {
        when (value.obtainedValueOrNull) {
            null -> {
                // source has **NOT** been used as build logic input:
                // serialize the source
                writeBoolean(true)
                encodeValueSource(value)
            }
            else -> {
                // source has been used as build logic input:
                // serialize the value directly as it will be part of the
                // cached state fingerprint.
                // Currently not necessary due to the unpacking that happens
                // to the TypeSanitizingProvider put around the ValueSourceProvider.
                throw IllegalStateException("build logic input")
            }
        }
    }

    override suspend fun ReadContext.decode(): ValueSourceProvider<*, *>? =
        when (readBoolean()) {
            true -> decodeValueSource()
            false -> throw IllegalStateException()
        }

    private
    suspend fun WriteContext.encodeValueSource(value: ValueSourceProvider<*, *>) {
        encodePreservingSharedIdentityOf(value) {
            value.run {
                writeClass(valueSourceType)
                writeClass(parametersType)
                write(parameters)
            }
        }
    }

    private
    suspend fun ReadContext.decodeValueSource(): ValueSourceProvider<*, *> =
        decodePreservingSharedIdentity {
            val valueSourceType = readClass()
            val parametersType = readClass()
            val parameters = read()!!
            val provider =
                valueSourceProviderFactory.instantiateValueSourceProvider<Any, ValueSourceParameters>(
                    valueSourceType.uncheckedCast(),
                    parametersType.uncheckedCast(),
                    parameters.uncheckedCast()
                )
            provider.uncheckedCast()
        }
}


class
PropertyCodec(private val propertyFactory: PropertyFactory, private val providerCodec: Codec<ProviderInternal<*>>) : Codec<DefaultProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultProperty<*>) {
        // TODO - should write the property type
        providerCodec.run { encode(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultProperty<*> {
        val provider = providerCodec.run { decode() }!!
        return propertyFactory.property(Any::class.java).provider(provider)
    }
}


class
DirectoryPropertyCodec(private val filePropertyFactory: FilePropertyFactory, private val providerCodec: Codec<ProviderInternal<*>>) : Codec<DefaultDirectoryVar> {
    override suspend fun WriteContext.encode(value: DefaultDirectoryVar) {
        providerCodec.run { encode(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultDirectoryVar {
        val provider: Provider<Directory> = providerCodec.run { decode() }!!.uncheckedCast()
        return filePropertyFactory.newDirectoryProperty().value(provider) as DefaultDirectoryVar
    }
}


class
RegularFilePropertyCodec(private val filePropertyFactory: FilePropertyFactory, private val providerCodec: Codec<ProviderInternal<*>>) : Codec<DefaultRegularFileVar> {
    override suspend fun WriteContext.encode(value: DefaultRegularFileVar) {
        providerCodec.run { encode(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultRegularFileVar {
        val provider: Provider<RegularFile> = providerCodec.run { decode() }!!.uncheckedCast()
        return filePropertyFactory.newFileProperty().value(provider) as DefaultRegularFileVar
    }
}


class
ListPropertyCodec(private val propertyFactory: PropertyFactory, private val providerCodec: Codec<ProviderInternal<*>>) : Codec<DefaultListProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultListProperty<*>) {
        // TODO - should write the element type
        writeCollection(value.providers) { providerCodec.run { encode(it) } }
    }

    override suspend fun ReadContext.decode(): DefaultListProperty<*> {
        val providers = readList { providerCodec.run { decode() } }
        return propertyFactory.listProperty(Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}


class
SetPropertyCodec(private val propertyFactory: PropertyFactory, private val providerCodec: Codec<ProviderInternal<*>>) : Codec<DefaultSetProperty<*>> {
    override suspend fun WriteContext.encode(value: DefaultSetProperty<*>) {
        // TODO - should write the element type
        writeCollection(value.providers) { providerCodec.run { encode(it) } }
    }

    override suspend fun ReadContext.decode(): DefaultSetProperty<*> {
        val providers = readList { providerCodec.run { decode() } }
        return propertyFactory.setProperty(Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}


class
MapPropertyCodec(private val propertyFactory: PropertyFactory, private val providerCodec: Codec<ProviderInternal<*>>) : Codec<DefaultMapProperty<*, *>> {
    override suspend fun WriteContext.encode(value: DefaultMapProperty<*, *>) {
        // TODO - should write the key and value types
        writeCollection(value.providers) { providerCodec.run { encode(it) } }
    }

    override suspend fun ReadContext.decode(): DefaultMapProperty<*, *> {
        val providers = readList { providerCodec.run { decode() } }
        return propertyFactory.mapProperty(Any::class.java, Any::class.java).apply {
            providers(providers.uncheckedCast())
        }
    }
}
