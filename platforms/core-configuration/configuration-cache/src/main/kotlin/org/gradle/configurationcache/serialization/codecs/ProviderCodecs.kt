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
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultDirectoryVar
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultRegularFileVar
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.DefaultListProperty
import org.gradle.api.internal.provider.DefaultMapProperty
import org.gradle.api.internal.provider.DefaultProperty
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
import org.gradle.api.services.internal.BuildServiceDetails
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.configurationcache.serialization.IsolateOwners
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.internal.flow.services.BuildWorkResultProvider
import org.gradle.internal.flow.services.RegisteredFlowAction
import org.gradle.internal.serialize.graph.Bindings
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.MutableIsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.logPropertyProblem
import org.gradle.internal.serialize.graph.readClassOf
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.withDebugFrame
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.serialize.graph.withPropertyTrace


internal
fun defaultCodecForProviderWithChangingValue(
    valueSourceProviderCodec: Codec<ValueSourceProvider<*, *>>,
    buildServiceProviderCodec: Codec<BuildServiceProvider<*, *>>,
    flowProvidersCodec: Codec<BuildWorkResultProvider>
) = Bindings.of {
    bind(valueSourceProviderCodec)
    bind(buildServiceProviderCodec)
    bind(flowProvidersCodec)
    bind(BeanCodec)
}.build()


/**
 * This is not used directly when encoding or decoding the object graph. This codec takes care of substituting a provider whose
 * value is known at configuration time with a fixed value.
 */
internal
class FixedValueReplacingProviderCodec(

    private
    val providerWithChangingValueCodec: Codec<Any?>

) {
    suspend fun WriteContext.encodeProvider(value: ProviderInternal<*>) {
        val state = value.calculateExecutionTimeValue()
        encodeValue(state)
    }

    suspend fun WriteContext.encodeValue(value: ValueSupplier.ExecutionTimeValue<*>) {
        val sideEffect = value.sideEffect
        when {
            value.isMissing -> {
                // Can serialize a fixed value and discard the provider
                // TODO - should preserve information about the source, for diagnostics at execution time
                writeByte(1)
            }

            value.hasFixedValue() && sideEffect == null -> {
                // Can serialize a fixed value and discard the provider
                // TODO - should preserve information about the source, for diagnostics at execution time
                writeByte(2)
                write(value.fixedValue)
            }

            value.hasFixedValue() && sideEffect != null -> {
                // Can serialize a fixed value and discard the provider
                // TODO - should preserve information about the source, for diagnostics at execution time
                writeByte(3)
                write(value.fixedValue)
                write(sideEffect)
            }

            else -> {
                // Cannot write a fixed value, so write the provider itself
                writeByte(4)
                providerWithChangingValueCodec.run { encode(value.changingValue) }
            }
        }
    }

    suspend fun ReadContext.decodeProvider(): ProviderInternal<*> {
        return decodeValue().toProvider()
    }

    suspend fun ReadContext.decodeValue(): ValueSupplier.ExecutionTimeValue<*> =
        when (readByte()) {
            1.toByte() -> ValueSupplier.ExecutionTimeValue.missing<Any>()
            2.toByte() -> ValueSupplier.ExecutionTimeValue.ofNullable(read()) // nullable because serialization may replace value with null, e.g. when using provider of Task
            3.toByte() -> {
                val value = read()
                val sideEffect = readNonNull<ValueSupplier.SideEffect<in Any>>()
                // nullable because serialization may replace value with null, e.g. when using provider of Task
                ValueSupplier.ExecutionTimeValue.ofNullable(value).withSideEffect(sideEffect)
            }

            4.toByte() -> ValueSupplier.ExecutionTimeValue.changingValue<Any>(providerWithChangingValueCodec.run { decode() }!!.uncheckedCast())
            else -> throw IllegalStateException("Unexpected provider value")
        }
}


internal
class FlowProvidersCodec(
    private val flowProviders: FlowProviders
) : Codec<BuildWorkResultProvider> {

    override suspend fun WriteContext.encode(value: BuildWorkResultProvider) {
        if (isolate.owner !is IsolateOwners.OwnerFlowAction) {
            logPropertyProblem("serialize") {
                reference(BuildWorkResultProvider::class)
                text(" can only be used as input to flow actions.")
            }
        }
    }

    override suspend fun ReadContext.decode(): BuildWorkResultProvider {
        return flowProviders.buildWorkResult.uncheckedCast()
    }
}


internal
object RegisteredFlowActionCodec : Codec<RegisteredFlowAction> {

    override suspend fun WriteContext.encode(value: RegisteredFlowAction) {
        val owner = verifiedIsolateOwner()
        val flowActionClass = value.type
        withDebugFrame({ flowActionClass.name }) {
            writeClass(flowActionClass)
            withFlowActionIsolate(flowActionClass, owner) {
                write(value.parameters)
            }
        }
    }

    override suspend fun ReadContext.decode(): RegisteredFlowAction {
        val flowActionClass = readClassOf<FlowAction<FlowParameters>>()
        withFlowActionIsolate(flowActionClass, verifiedIsolateOwner()) {
            return RegisteredFlowAction(flowActionClass, read()?.uncheckedCast())
        }
    }

    private
    inline fun <T : MutableIsolateContext, R> T.withFlowActionIsolate(flowActionClass: Class<*>, owner: IsolateOwners.OwnerFlowScope, block: T.() -> R): R {
        withIsolate(IsolateOwners.OwnerFlowAction(owner)) {
            withPropertyTrace(PropertyTrace.BuildLogicClass(flowActionClass.name)) {
                return block()
            }
        }
    }

    private
    fun IsolateContext.verifiedIsolateOwner(): IsolateOwners.OwnerFlowScope {
        val owner = isolate.owner
        require(owner is IsolateOwners.OwnerFlowScope) {
            "Flow actions must belong to a Flow scope!"
        }
        return owner
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
            val serviceDetails: BuildServiceDetails<*, *> = value.serviceDetails
            write(serviceDetails.buildIdentifier)
            writeString(serviceDetails.name)
            writeClass(serviceDetails.implementationType)
            writeBoolean(serviceDetails.isResolved)
            if (serviceDetails.isResolved) {
                write(serviceDetails.parameters)
                writeInt(serviceDetails.maxUsages)
            }
        }
    }

    override suspend fun ReadContext.decode(): BuildServiceProvider<*, *>? =
        decodePreservingSharedIdentity {
            val buildIdentifier = readNonNull<BuildIdentifier>()
            val name = readString()
            val implementationType = readClassOf<BuildService<*>>()
            val isResolved = readBoolean()
            if (isResolved) {
                val parameters = read() as BuildServiceParameters?
                val maxUsages = readInt()
                buildServiceRegistryOf(buildIdentifier).registerIfAbsent(name, implementationType, parameters, maxUsages)
            } else {
                buildServiceRegistryOf(buildIdentifier).consume(name, implementationType)
            }
        }

    private
    fun buildServiceRegistryOf(buildIdentifier: BuildIdentifier) =
        buildStateRegistry.getBuild(buildIdentifier).mutableModel.serviceOf<BuildServiceRegistryInternal>()
}


internal
class ValueSourceProviderCodec(
    private val valueSourceProviderFactory: ValueSourceProviderFactory
) : Codec<ValueSourceProvider<*, *>> {

    override suspend fun WriteContext.encode(value: ValueSourceProvider<*, *>) {
        if (!value.hasBeenObtained()) {
            // source has **NOT** been used as build logic input:
            // serialize the source
            writeBoolean(true)
            encodeValueSource(value)
        } else {
            // source has been used as build logic input:
            // serialize the value directly as it will be part of the
            // cached state fingerprint.
            // Currently not necessary due to the unpacking that happens
            // to the TypeSanitizingProvider put around the ValueSourceProvider.
            throw IllegalStateException("build logic input")
        }
    }

    override suspend fun ReadContext.decode(): ValueSourceProvider<*, *> =
        when (readBoolean()) {
            true -> decodeValueSource()
            false -> throw IllegalStateException()
        }

    private
    suspend fun WriteContext.encodeValueSource(value: ValueSourceProvider<*, *>) {
        encodePreservingSharedIdentityOf(value) {
            value.run {
                val hasParameters = parametersType != null
                writeClass(valueSourceType)
                writeBoolean(hasParameters)
                if (hasParameters) {
                    writeClass(parametersType as Class<*>)
                    write(parameters)
                }
            }
        }
    }

    private
    suspend fun ReadContext.decodeValueSource(): ValueSourceProvider<*, *> =
        decodePreservingSharedIdentity {
            val valueSourceType = readClass()
            val hasParameters = readBoolean()
            val parametersType = if (hasParameters) readClass() else null
            val parameters = if (hasParameters) read()!! else null

            val provider =
                valueSourceProviderFactory.instantiateValueSourceProvider<Any, ValueSourceParameters>(
                    valueSourceType.uncheckedCast(),
                    parametersType?.uncheckedCast(),
                    parameters?.uncheckedCast()
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
        encodePreservingIdentityOf(value) {
            writeClass(value.type as Class<*>)
            providerCodec.run { encodeProvider(value.provider) }
        }
    }

    override suspend fun ReadContext.decode(): DefaultProperty<*> {
        return decodePreservingIdentity { id ->
            val type: Class<Any> = readClass().uncheckedCast()
            val provider = providerCodec.run { decodeProvider() }
            val property = propertyFactory.property(type).provider(provider)
            isolate.identities.putInstance(id, property)
            property
        }
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
