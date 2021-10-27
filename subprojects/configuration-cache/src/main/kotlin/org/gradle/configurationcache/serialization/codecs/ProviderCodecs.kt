/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultDirectoryVar
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultRegularFileVar
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.DefaultListProperty
import org.gradle.api.internal.provider.DefaultMapProperty
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.DefaultSetProperty
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory.ValueSourceProvider
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodePreservingSharedIdentity
import org.gradle.configurationcache.serialization.encodePreservingSharedIdentityOf
import org.gradle.configurationcache.serialization.logPropertyProblem
import org.gradle.configurationcache.serialization.readClassOf
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.internal.build.BuildStateRegistry


/**
 * This is not used directly when encoding or decoding the object graph. This codec takes care of substituting a provider whose
 * value is known at configuration time with a fixed value.
 */
internal
class FixedValueReplacingProviderCodec(
    valueSourceProviderFactory: ValueSourceProviderFactory,
    buildStateRegistry: BuildStateRegistry
) {
    private
    val providerWithChangingValueCodec = BindingsBackedCodec {
        bind(ValueSourceProviderCodec(valueSourceProviderFactory))
        bind(BuildServiceProviderCodec(buildStateRegistry))
        bind(BeanCodec())
    }

    suspend fun WriteContext.encodeProvider(value: ProviderInternal<*>) {
        val state = try {
            value.calculateExecutionTimeValue()
        } catch (e: Exception) {
            logPropertyProblem("serialize", e) {
                text("value ")
                reference(value.toString())
                text(" failed to unpack provider")
            }
            writeByte(0)
            write(BrokenValue(e))
            return
        }
        encodeValue(state)
    }

    suspend fun WriteContext.encodeValue(value: ValueSupplier.ExecutionTimeValue<*>) {
        when {
            value.isMissing -> {
                // Can serialize a fixed value and discard the provider
                // TODO - should preserve information about the source, for diagnostics at execution time
                writeByte(1)
            }
            value.isFixedValue -> {
                // Can serialize a fixed value and discard the provider
                // TODO - should preserve information about the source, for diagnostics at execution time
                writeByte(2)
                write(value.fixedValue)
            }
            else -> {
                // Cannot write a fixed value, so write the provider itself
                writeByte(3)
                providerWithChangingValueCodec.run { encode(value.changingValue) }
            }
        }
    }

    suspend fun ReadContext.decodeProvider(): ProviderInternal<*> {
        return decodeValue().toProvider()
    }

    suspend fun ReadContext.decodeValue(): ValueSupplier.ExecutionTimeValue<*> =
        when (readByte()) {
            0.toByte() -> {
                val value = read() as BrokenValue
                ValueSupplier.ExecutionTimeValue.changingValue(DefaultProvider { value.rethrow() })
            }
            1.toByte() -> ValueSupplier.ExecutionTimeValue.missing<Any>()
            2.toByte() -> ValueSupplier.ExecutionTimeValue.ofNullable(read()) // nullable because serialization may replace value with null, eg when using provider of Task
            3.toByte() -> ValueSupplier.ExecutionTimeValue.changingValue<Any>(providerWithChangingValueCodec.run { decode() }!!.uncheckedCast())
            else -> throw IllegalStateException("Unexpected provider value")
        }
}


/**
 * Handles Provider instances seen in the object graph, and delegates to another codec that handles the value.
 */
internal
class ProviderCodec(
    private val providerCodec: FixedValueReplacingProviderCodec
) : Codec<ProviderInternal<*>> {

    override suspend fun WriteContext.encode(value: ProviderInternal<*>) {
        // TODO - should write the provider value type
        providerCodec.run { encodeProvider(value) }
    }

    override suspend fun ReadContext.decode() =
        providerCodec.run { decodeProvider() }
}


internal
class BuildServiceProviderCodec(
    private val buildStateRegistry: BuildStateRegistry
) : Codec<BuildServiceProvider<*, *>> {

    override suspend fun WriteContext.encode(value: BuildServiceProvider<*, *>) {
        encodePreservingSharedIdentityOf(value) {
            val buildIdentifier = value.buildIdentifier
            write(buildIdentifier)
            writeString(value.name)
            writeClass(value.implementationType)
            write(value.parameters)
            writeInt(
                buildServiceRegistryOf(buildIdentifier).forService(value).maxUsages
            )
        }
    }

    override suspend fun ReadContext.decode(): BuildServiceProvider<*, *>? =
        decodePreservingSharedIdentity {
            val buildIdentifier = readNonNull<BuildIdentifier>()
            val name = readString()
            val implementationType = readClassOf<BuildService<*>>()
            val parameters = read() as BuildServiceParameters?
            val maxUsages = readInt()
            buildServiceRegistryOf(buildIdentifier).register(name, implementationType, parameters, maxUsages)
        }

    private
    fun buildServiceRegistryOf(buildIdentifier: BuildIdentifier) =
        gradleOf(buildIdentifier).serviceOf<BuildServiceRegistryInternal>()

    private
    fun gradleOf(buildIdentifier: BuildIdentifier) =
        when (buildIdentifier) {
            DefaultBuildIdentifier.ROOT -> buildStateRegistry.rootBuild.build
            else -> buildStateRegistry.getIncludedBuild(buildIdentifier).configuredBuild
        }
}


internal
class ValueSourceProviderCodec(
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
                writeClass(parametersType as Class<*>)
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


internal
class PropertyCodec(
    private val propertyFactory: PropertyFactory,
    private val providerCodec: FixedValueReplacingProviderCodec
) : Codec<DefaultProperty<*>> {

    override suspend fun WriteContext.encode(value: DefaultProperty<*>) {
        writeClass(value.type as Class<*>)
        providerCodec.run { encodeProvider(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultProperty<*> {
        val type: Class<Any> = readClass().uncheckedCast()
        val provider = providerCodec.run { decodeProvider() }
        return propertyFactory.property(type).provider(provider)
    }
}


internal
class DirectoryPropertyCodec(
    private val filePropertyFactory: FilePropertyFactory,
    private val providerCodec: FixedValueReplacingProviderCodec
) : Codec<DefaultDirectoryVar> {

    override suspend fun WriteContext.encode(value: DefaultDirectoryVar) {
        providerCodec.run { encodeProvider(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultDirectoryVar {
        val provider: Provider<Directory> = providerCodec.run { decodeProvider() }.uncheckedCast()
        return filePropertyFactory.newDirectoryProperty().value(provider) as DefaultDirectoryVar
    }
}


internal
class RegularFilePropertyCodec(
    private val filePropertyFactory: FilePropertyFactory,
    private val providerCodec: FixedValueReplacingProviderCodec
) : Codec<DefaultRegularFileVar> {

    override suspend fun WriteContext.encode(value: DefaultRegularFileVar) {
        providerCodec.run { encodeProvider(value.provider) }
    }

    override suspend fun ReadContext.decode(): DefaultRegularFileVar {
        val provider: Provider<RegularFile> = providerCodec.run { decodeProvider() }.uncheckedCast()
        return filePropertyFactory.newFileProperty().value(provider) as DefaultRegularFileVar
    }
}


internal
class ListPropertyCodec(
    private val propertyFactory: PropertyFactory,
    private val providerCodec: FixedValueReplacingProviderCodec
) : Codec<DefaultListProperty<*>> {

    override suspend fun WriteContext.encode(value: DefaultListProperty<*>) {
        writeClass(value.elementType)
        providerCodec.run { encodeValue(value.calculateExecutionTimeValue()) }
    }

    override suspend fun ReadContext.decode(): DefaultListProperty<*> {
        val type: Class<Any> = readClass().uncheckedCast()
        val value: ValueSupplier.ExecutionTimeValue<List<Any>> = providerCodec.run { decodeValue() }.uncheckedCast()
        return propertyFactory.listProperty(type).apply {
            fromState(value)
        }
    }
}


internal
class SetPropertyCodec(
    private val propertyFactory: PropertyFactory,
    private val providerCodec: FixedValueReplacingProviderCodec
) : Codec<DefaultSetProperty<*>> {

    override suspend fun WriteContext.encode(value: DefaultSetProperty<*>) {
        writeClass(value.elementType)
        providerCodec.run { encodeValue(value.calculateExecutionTimeValue()) }
    }

    override suspend fun ReadContext.decode(): DefaultSetProperty<*> {
        val type: Class<Any> = readClass().uncheckedCast()
        val value: ValueSupplier.ExecutionTimeValue<Set<Any>> = providerCodec.run { decodeValue() }.uncheckedCast()
        return propertyFactory.setProperty(type).apply {
            fromState(value)
        }
    }
}


internal
class MapPropertyCodec(
    private val propertyFactory: PropertyFactory,
    private val providerCodec: FixedValueReplacingProviderCodec
) : Codec<DefaultMapProperty<*, *>> {

    override suspend fun WriteContext.encode(value: DefaultMapProperty<*, *>) {
        writeClass(value.keyType)
        writeClass(value.valueType)
        providerCodec.run { encodeValue(value.calculateExecutionTimeValue()) }
    }

    override suspend fun ReadContext.decode(): DefaultMapProperty<*, *> {
        val keyType: Class<Any> = readClass().uncheckedCast()
        val valueType: Class<Any> = readClass().uncheckedCast()
        val state: ValueSupplier.ExecutionTimeValue<Map<Any, Any>> = providerCodec.run { decodeValue() }.uncheckedCast()
        return propertyFactory.mapProperty(keyType, valueType).apply {
            fromState(state)
        }
    }
}
