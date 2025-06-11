/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultDirectoryVar
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultRegularFileVar
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.provider.DefaultListProperty
import org.gradle.api.internal.provider.DefaultMapProperty
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.DefaultSetProperty
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory.ValueSourceProvider
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.provider.PropertyHost
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
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.base.serialize.RuntimeTaskCheckingPropertyHost
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.flow.services.BuildWorkResultProvider
import org.gradle.internal.flow.services.RegisteredFlowAction
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.MutableIsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.BeanCodec
import org.gradle.internal.serialize.graph.codecs.Bindings
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodeBean
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.logPropertyProblem
import org.gradle.internal.serialize.graph.readClassOf
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.withDebugFrame
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.serialize.graph.withPropertyTrace
import org.gradle.internal.service.scopes.ProjectBackedPropertyHost
import org.gradle.util.Path


fun defaultCodecForProviderWithChangingValue(
    valueSourceProviderCodec: Codec<ValueSourceProvider<*, *>>,
    buildServiceProviderCodec: Codec<BuildServiceProvider<*, *>>,
    flowProvidersCodec: Codec<BuildWorkResultProvider>
) = Bindings.of {
    bind(valueSourceProviderCodec)
    bind(buildServiceProviderCodec)
    bind(BuildServiceParameterCodec)
    bind(flowProvidersCodec)
    bind(BeanCodec)
}.build()


/**
 * This is not used directly when encoding or decoding the object graph. This codec takes care of substituting a provider whose
 * value is known at configuration time with a fixed value.
 */
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
            else -> error("Unexpected provider value")
        }
}


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


class BuildServiceProviderCodec(
    private val buildStateRegistry: BuildStateRegistry
) : Codec<BuildServiceProvider<*, *>> {

    override suspend fun WriteContext.encode(value: BuildServiceProvider<*, *>) =
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

    override suspend fun ReadContext.decode(): BuildServiceProvider<*, *> =
        decodePreservingSharedIdentity<BuildServiceProvider<*, *>> {
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


object BuildServiceParameterCodec : Codec<BuildServiceParameters> {
    override suspend fun WriteContext.encode(value: BuildServiceParameters) =
        writeSharedObject(value) {
            encodeBean(value)
        }

    override suspend fun ReadContext.decode(): BuildServiceParameters =
        readSharedObject {
            decodeBean()
        }.uncheckedCast()
}


class ValueSourceProviderCodec(
    private val valueSourceProviderFactory: ValueSourceProviderFactory
) : Codec<ValueSourceProvider<*, *>> {

    override suspend fun WriteContext.encode(value: ValueSourceProvider<*, *>) {
        writeSharedObject(value) {
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
                error("build logic input")
            }
        }
    }

    override suspend fun ReadContext.decode(): ValueSourceProvider<*, *> =
        readSharedObject {
            when (readBoolean()) {
                true -> decodeValueSource()
                false -> error("Unexpected boolean value (false) while decoding")
            }
        }

    private
    suspend fun WriteContext.encodeValueSource(value: ValueSourceProvider<*, *>) {
        // TODO:configuration-cache `encodePreservingSharedIdentityOf` should be unnecessary for shared objects
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
        // TODO:configuration-cache `decodePreservingSharedIdentity` should be unnecessary for shared objects
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


class PropertyCodec(
    private val propertyFactory: PropertyFactory,
    private val providerCodec: FixedValueReplacingProviderCodec
) : Codec<DefaultProperty<*>> {

    override suspend fun WriteContext.encode(value: DefaultProperty<*>) {
        encodePreservingIdentityOf(value) {
            writeClass(value.type)
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


/**
 * Base class for codecs that handle file-based properties, that extend [org.gradle.api.internal.file.DefaultFilePropertyFactory.AbstractFileVar],
 * such as [DefaultDirectoryVar] and [DefaultRegularFileVar].
 *
 * Responsible for encoding the property "metadata" fields (like `isDisallowChanges`), but delegates
 * the actual value decoding and instantiation to concrete subclasses.
 */
abstract class AbstractFileVarPropertyCodec<P: DefaultFilePropertyFactory.AbstractFileVar<*, *>> (
    protected val filePropertyFactory: FilePropertyFactory,
    protected val providerCodec: FixedValueReplacingProviderCodec
) : Codec<P> {
    override suspend fun WriteContext.encode(value: P) {
        write(value.fileResolver)
        if (value.fileCollectionResolver == null) {
            writeByte(0.toByte())
        } else {
            writeByte(1.toByte())
            write(value.fileCollectionResolver)
        }
        writeBoolean(value.isDisallowUnsafeRead)
        writeBoolean(value.isDisallowChanges)
        writeHost(value)
        providerCodec.run {
            encodeProvider(value.provider)
        }
    }

    override suspend fun ReadContext.decode(): P {
        val resolver = readNonNull<PathToFileResolver>()
        require(resolver is FileResolver) {
            "Expected decoded resolver to be a FileResolver, but was ${resolver::class.java.name}"
        }
        val fileCollectionResolver = when (val discriminator = readByte()) {
            0.toByte() -> null
            1.toByte() -> readNonNull<PathToFileResolver>().uncheckedCast<PathToFileResolver>()
            else -> error("Unsupported PathToFileResolver discriminator byte value: $discriminator")
        }
        val isDisallowUnsafeRead = readBoolean()
        val isDisallowChanges = readBoolean()
        val host: PropertyHost? = readHost()
        return restorePropertyWithValue(host, resolver, fileCollectionResolver).apply {
            if (isDisallowUnsafeRead) {
                disallowUnsafeRead()
            }
            if (isDisallowChanges) {
                disallowChanges()
            }
        }
    }
    private suspend fun WriteContext.writeHost(value: P) {
        val host = value.host
        when {
            host == null -> writeByte(0)
            host == PropertyHost.NO_OP -> {
                writeByte(1)
            }
            host is ProjectBackedPropertyHost -> {
                writeByte(2)
                writeProducer(value)
            }
            else -> error("Unsupported host type: ${host::class.java.name}")
        }
    }

    private suspend fun WriteContext.writeProducer(value : P) {
        if (value.producer is ValueSupplier.TaskProducer) {
            writeTaskProducer(value.producer as ValueSupplier.TaskProducer)
        } else {
            error("Unsupported producer type: ${value.javaClass.name}")
        }
    }

    private suspend fun WriteContext.writeTaskProducer(taskProducer : ValueSupplier.TaskProducer) {
        var task : TaskInternal? = null
        taskProducer.visitProducerTasks { task = this as TaskInternal}
        write(task?.identityPath)
    }

    private suspend fun ReadContext.readHost(): PropertyHost? {
        val discriminator = readByte()
        when (discriminator) {
            0.toByte() -> {
                return null
            }
            1.toByte() -> {
                return PropertyHost.NO_OP
            }
            2.toByte() -> {
                val taskPath: Path = read() as Path
                return RuntimeTaskCheckingPropertyHost(taskPath)
            }
            else -> {
                error("Unsupported host type: $discriminator")
            }
        }
    }

    /**
     * Decodes the provider and creates a new [org.gradle.api.provider.Property] of type [P], setting its value
     * by restoring the serialized value but not affecting any "metadata" fields (like `isDisallowChanges`)
     * present in the property.
     *
     * @param host the [PropertyHost] to use for the property, or null if not applicable
     * @param fileResolver the [FileResolver] to use for resolving file paths
     * @param fileCollectionResolver the [PathToFileResolver] to use for resolving file collections, or null if not applicable
     * @return the property with the decoded provider value set
     */
    abstract suspend fun ReadContext.restorePropertyWithValue(host: PropertyHost?, fileResolver: FileResolver, fileCollectionResolver: PathToFileResolver?): P
}


class DirectoryPropertyCodec(
    filePropertyFactory: FilePropertyFactory,
    providerCodec: FixedValueReplacingProviderCodec
) : AbstractFileVarPropertyCodec<DefaultDirectoryVar>(filePropertyFactory, providerCodec) {
    override suspend fun ReadContext.restorePropertyWithValue(host: PropertyHost?, fileResolver: FileResolver, fileCollectionResolver: PathToFileResolver?): DefaultDirectoryVar {
        val provider: Provider<Directory> = providerCodec.run { decodeProvider() }.uncheckedCast()
        val newPropFactory = filePropertyFactory.withResolvers(host, fileResolver, fileCollectionResolver!!)
        return newPropFactory.newDirectoryProperty().value(provider) as DefaultDirectoryVar
    }
}


class RegularFilePropertyCodec(
    filePropertyFactory: FilePropertyFactory,
    providerCodec: FixedValueReplacingProviderCodec
) : AbstractFileVarPropertyCodec<DefaultRegularFileVar>(filePropertyFactory, providerCodec) {
    override suspend fun ReadContext.restorePropertyWithValue(host: PropertyHost?, fileResolver: FileResolver, fileCollectionResolver: PathToFileResolver?): DefaultRegularFileVar {
        val provider: Provider<RegularFile> = providerCodec.run { decodeProvider() }.uncheckedCast()
        val newPropFactory = filePropertyFactory.withResolver(host, fileResolver)
        val newProp = newPropFactory.newFileProperty().value(provider) as DefaultRegularFileVar
        return newProp.value(provider) as DefaultRegularFileVar
    }
}


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
