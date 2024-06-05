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

package org.gradle.configurationcache.serialization

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.flow.FlowProviders
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.CapabilitySerializer
import org.gradle.api.internal.artifacts.transform.TransformActionScheme
import org.gradle.api.internal.artifacts.transform.TransformParameterScheme
import org.gradle.api.internal.artifacts.transform.TransformStepNode
import org.gradle.api.internal.artifacts.transform.TransformStepNodeFactory
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.provider.PropertyFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.configurationcache.serialization.codecs.ActionNodeCodec
import org.gradle.configurationcache.serialization.codecs.ApiTextResourceAdapterCodec
import org.gradle.configurationcache.serialization.codecs.BooleanValueSnapshotCodec
import org.gradle.configurationcache.serialization.codecs.BuildServiceProviderCodec
import org.gradle.configurationcache.serialization.codecs.CachedEnvironmentStateCodec
import org.gradle.configurationcache.serialization.codecs.CalculatedValueContainerCodec
import org.gradle.configurationcache.serialization.codecs.ConfigurableFileCollectionCodec
import org.gradle.configurationcache.serialization.codecs.ConfigurableFileTreeCodec
import org.gradle.configurationcache.serialization.codecs.DefaultContextAwareTaskLoggerCodec
import org.gradle.configurationcache.serialization.codecs.DefaultCopySpecCodec
import org.gradle.configurationcache.serialization.codecs.DestinationRootCopySpecCodec
import org.gradle.configurationcache.serialization.codecs.DirectoryCodec
import org.gradle.configurationcache.serialization.codecs.DirectoryPropertyCodec
import org.gradle.configurationcache.serialization.codecs.FileCollectionCodec
import org.gradle.configurationcache.serialization.codecs.FileTreeCodec
import org.gradle.configurationcache.serialization.codecs.FileValueSnapshotCodec
import org.gradle.configurationcache.serialization.codecs.FixedValueReplacingProviderCodec
import org.gradle.configurationcache.serialization.codecs.FlowProvidersCodec
import org.gradle.configurationcache.serialization.codecs.IntegerValueSnapshotCodec
import org.gradle.configurationcache.serialization.codecs.IntersectionPatternSetCodec
import org.gradle.configurationcache.serialization.codecs.IsolatedArrayCodec
import org.gradle.configurationcache.serialization.codecs.IsolatedEnumValueSnapshotCodec
import org.gradle.configurationcache.serialization.codecs.IsolatedImmutableManagedValueCodec
import org.gradle.configurationcache.serialization.codecs.IsolatedJavaSerializedValueSnapshotCodec
import org.gradle.configurationcache.serialization.codecs.IsolatedListCodec
import org.gradle.configurationcache.serialization.codecs.IsolatedManagedValueCodec
import org.gradle.configurationcache.serialization.codecs.IsolatedMapCodec
import org.gradle.configurationcache.serialization.codecs.IsolatedSetCodec
import org.gradle.configurationcache.serialization.codecs.ListPropertyCodec
import org.gradle.configurationcache.serialization.codecs.LoggerCodec
import org.gradle.configurationcache.serialization.codecs.MapEntrySnapshotCodec
import org.gradle.configurationcache.serialization.codecs.MapPropertyCodec
import org.gradle.configurationcache.serialization.codecs.NullValueSnapshotCodec
import org.gradle.configurationcache.serialization.codecs.OrdinalNodeCodec
import org.gradle.configurationcache.serialization.codecs.PatternSetCodec
import org.gradle.configurationcache.serialization.codecs.PropertyCodec
import org.gradle.configurationcache.serialization.codecs.ProviderCodec
import org.gradle.configurationcache.serialization.codecs.RegisteredFlowActionCodec
import org.gradle.configurationcache.serialization.codecs.RegularFileCodec
import org.gradle.configurationcache.serialization.codecs.RegularFilePropertyCodec
import org.gradle.configurationcache.serialization.codecs.SerializedLambdaParametersCheckingCodec
import org.gradle.configurationcache.serialization.codecs.SetPropertyCodec
import org.gradle.configurationcache.serialization.codecs.StringValueSnapshotCodec
import org.gradle.configurationcache.serialization.codecs.TaskInAnotherBuildCodec
import org.gradle.configurationcache.serialization.codecs.TaskNodeCodec
import org.gradle.configurationcache.serialization.codecs.TaskReferenceCodec
import org.gradle.configurationcache.serialization.codecs.UnsupportedFingerprintBuildServiceProviderCodec
import org.gradle.configurationcache.serialization.codecs.UnsupportedFingerprintFlowProviders
import org.gradle.configurationcache.serialization.codecs.ValueSourceProviderCodec
import org.gradle.configurationcache.serialization.codecs.WorkNodeActionCodec
import org.gradle.configurationcache.serialization.codecs.WorkNodeCodec
import org.gradle.configurationcache.serialization.codecs.defaultCodecForProviderWithChangingValue
import org.gradle.configurationcache.serialization.codecs.groovyCodecs
import org.gradle.configurationcache.serialization.codecs.jos.ExternalizableCodec
import org.gradle.configurationcache.serialization.codecs.jos.JavaObjectSerializationCodec
import org.gradle.configurationcache.serialization.codecs.jos.JavaSerializationEncodingLookup
import org.gradle.configurationcache.serialization.codecs.unsupportedTypes
import org.gradle.internal.serialize.codecs.dm.transform.CalculateArtifactsCodec
import org.gradle.internal.serialize.codecs.dm.transform.ChainedTransformStepNodeCodec
import org.gradle.internal.serialize.codecs.dm.transform.ComponentVariantIdentifierCodec
import org.gradle.internal.serialize.codecs.dm.transform.DefaultTransformCodec
import org.gradle.internal.serialize.codecs.dm.transform.FinalizeTransformDependenciesNodeCodec
import org.gradle.internal.serialize.codecs.dm.transform.InitialTransformStepNodeCodec
import org.gradle.internal.serialize.codecs.dm.transform.IsolateTransformParametersCodec
import org.gradle.internal.serialize.codecs.dm.transform.TransformChainCodec
import org.gradle.internal.serialize.codecs.dm.transform.TransformStepCodec
import org.gradle.internal.serialize.codecs.dm.transform.TransformStepSpecCodec
import org.gradle.internal.serialize.codecs.dm.transform.TransformedArtifactCodec
import org.gradle.internal.serialize.codecs.dm.transform.TransformedExternalArtifactSetCodec
import org.gradle.internal.serialize.codecs.dm.transform.TransformedProjectArtifactSetCodec
import org.gradle.execution.plan.OrdinalGroupFactory
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory.HASHCODE_SERIALIZER
import org.gradle.internal.serialize.codecs.dm.ArtifactCollectionCodec
import org.gradle.internal.serialize.codecs.dm.AttributeContainerCodec
import org.gradle.internal.serialize.codecs.dm.DefaultResolvableArtifactCodec
import org.gradle.internal.serialize.codecs.dm.ImmutableAttributesCodec
import org.gradle.internal.serialize.codecs.dm.LocalFileDependencyBackedArtifactSetCodec
import org.gradle.internal.serialize.codecs.dm.PublishArtifactLocalArtifactMetadataCodec
import org.gradle.internal.serialize.codecs.dm.ResolveArtifactNodeCodec
import org.gradle.internal.serialize.codecs.stdlib.ProxyCodec
import org.gradle.internal.serialize.graph.codecs.BeanSpecCodec
import org.gradle.internal.serialize.graph.codecs.Bindings
import org.gradle.internal.serialize.graph.codecs.BindingsBuilder
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.codecs.BeanCodec
import org.gradle.internal.serialize.graph.codecs.DelegatingCodec
import org.gradle.internal.serialize.graph.codecs.NotImplementedCodec
import org.gradle.internal.serialize.graph.codecs.ServicesCodec
import org.gradle.internal.serialize.graph.reentrant
import org.gradle.internal.state.ManagedFactoryRegistry


internal
class Codecs(
    directoryFileTreeFactory: DirectoryFileTreeFactory,
    fileCollectionFactory: FileCollectionFactory,
    artifactSetConverter: ArtifactSetToFileCollectionFactory,
    fileLookup: FileLookup,
    propertyFactory: PropertyFactory,
    filePropertyFactory: FilePropertyFactory,
    fileResolver: FileResolver,
    objectFactory: ObjectFactory,
    instantiator: Instantiator,
    fileSystemOperations: FileSystemOperations,
    val taskNodeFactory: TaskNodeFactory,
    val ordinalGroupFactory: OrdinalGroupFactory,
    inputFingerprinter: InputFingerprinter,
    buildOperationRunner: BuildOperationRunner,
    classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    isolatableFactory: IsolatableFactory,
    managedFactoryRegistry: ManagedFactoryRegistry,
    parameterScheme: TransformParameterScheme,
    actionScheme: TransformActionScheme,
    attributesFactory: ImmutableAttributesFactory,
    valueSourceProviderFactory: ValueSourceProviderFactory,
    calculatedValueContainerFactory: CalculatedValueContainerFactory,
    patternSetFactory: Factory<PatternSet>,
    fileOperations: FileOperations,
    fileFactory: FileFactory,
    includedTaskGraph: BuildTreeWorkGraphController,
    buildStateRegistry: BuildStateRegistry,
    documentationRegistry: DocumentationRegistry,
    val javaSerializationEncodingLookup: JavaSerializationEncodingLookup,
    flowProviders: FlowProviders,
    transformStepNodeFactory: TransformStepNodeFactory,
) {
    private
    val userTypesBindings: Bindings

    private
    val fingerprintUserTypesBindings: Bindings

    init {
        fun makeUserTypeBindings(providersBlock: BindingsBuilder.() -> Unit) = Bindings.of {
            unsupportedTypes()

            baseTypes()

            bind(HASHCODE_SERIALIZER)

            providersBlock()

            bind(DefaultContextAwareTaskLoggerCodec)
            bind(LoggerCodec)

            fileCollectionTypes(directoryFileTreeFactory, fileCollectionFactory, artifactSetConverter, fileOperations, fileFactory, patternSetFactory)

            bind(ApiTextResourceAdapterCodec)

            groovyCodecs()
            bind(SerializedLambdaParametersCheckingCodec)

            // Dependency management types
            bind(ArtifactCollectionCodec(calculatedValueContainerFactory, artifactSetConverter))
            bind(ImmutableAttributesCodec(attributesFactory, managedFactoryRegistry))
            bind(AttributeContainerCodec(attributesFactory, managedFactoryRegistry))
            bind(ComponentVariantIdentifierCodec)
            bind(InitialTransformStepNodeCodec(transformStepNodeFactory, buildOperationRunner, calculatedValueContainerFactory))
            bind(ChainedTransformStepNodeCodec(transformStepNodeFactory, buildOperationRunner, calculatedValueContainerFactory))
            bind(TransformStepCodec(inputFingerprinter))
            bind(TransformChainCodec())
            bind(DefaultTransformCodec(fileLookup, actionScheme))
            bind(DefaultResolvableArtifactCodec(calculatedValueContainerFactory))
            bind(TransformStepSpecCodec)
            bind(PublishArtifactLocalArtifactMetadataCodec)
            bind(TransformedProjectArtifactSetCodec())
            bind(TransformedExternalArtifactSetCodec())
            bind(CalculateArtifactsCodec(calculatedValueContainerFactory))
            bind(TransformedArtifactCodec(calculatedValueContainerFactory))
            bind(LocalFileDependencyBackedArtifactSetCodec(instantiator, attributesFactory, calculatedValueContainerFactory))
            bind(CalculatedValueContainerCodec(calculatedValueContainerFactory))
            bind(IsolateTransformParametersCodec(parameterScheme, isolatableFactory, buildOperationRunner, classLoaderHierarchyHasher, fileCollectionFactory, documentationRegistry))
            bind(FinalizeTransformDependenciesNodeCodec())
            bind(ResolveArtifactNodeCodec)
            bind(WorkNodeActionCodec)
            bind(CapabilitySerializer())

            bind(DefaultCopySpecCodec(patternSetFactory, fileCollectionFactory, objectFactory, instantiator, fileSystemOperations))
            bind(DestinationRootCopySpecCodec(fileResolver))

            bind(TaskReferenceCodec)

            bind(CachedEnvironmentStateCodec)

            bind(IsolatedManagedValueCodec(managedFactoryRegistry))
            bind(IsolatedImmutableManagedValueCodec(managedFactoryRegistry))
            bind(IsolatedJavaSerializedValueSnapshotCodec)
            bind(IsolatedArrayCodec)
            bind(IsolatedSetCodec)
            bind(IsolatedListCodec)
            bind(IsolatedMapCodec)
            bind(MapEntrySnapshotCodec)
            bind(IsolatedEnumValueSnapshotCodec)
            bind(StringValueSnapshotCodec)
            bind(IntegerValueSnapshotCodec)
            bind(FileValueSnapshotCodec)
            bind(BooleanValueSnapshotCodec)
            bind(NullValueSnapshotCodec)

            bind(ServicesCodec)

            bind(ProxyCodec)

            bind(BeanSpecCodec)

            bind(RegisteredFlowActionCodec)
        }

        userTypesBindings = makeUserTypeBindings {
            providerTypes(
                propertyFactory,
                filePropertyFactory,
                nestedProviderCodec(
                    valueSourceProviderFactory,
                    buildStateRegistry,
                    flowProviders
                )
            )
        }

        fingerprintUserTypesBindings = makeUserTypeBindings {
            providerTypes(
                propertyFactory,
                filePropertyFactory,
                nestedProviderCodecForFingerprint(
                    valueSourceProviderFactory
                )
            )
        }
    }

    private
    fun Bindings.completeWithStatefulCodecs() = append {
        bind(ExternalizableCodec)
        bind(JavaObjectSerializationCodec(javaSerializationEncodingLookup))

        // This protects the BeanCodec against StackOverflowErrors, but
        // we can still get them for the other codecs, for instance,
        // with deeply nested Lists, deeply nested Maps, etc.
        // The reentrant codec is stateful, and cannot be cached because of it.
        bind(reentrant(BeanCodec))
    }.build()

    fun userTypesCodec(): Codec<Any?> = userTypesBindings.completeWithStatefulCodecs()

    fun fingerprintTypesCodec(): Codec<Any?> = fingerprintUserTypesBindings.completeWithStatefulCodecs()

    private
    val internalTypesBindings = Bindings.of {
        baseTypes()

        providerTypes(propertyFactory, filePropertyFactory, nestedProviderCodec(valueSourceProviderFactory, buildStateRegistry, flowProviders))
        fileCollectionTypes(directoryFileTreeFactory, fileCollectionFactory, artifactSetConverter, fileOperations, fileFactory, patternSetFactory)

        bind(TaskInAnotherBuildCodec(includedTaskGraph))

        bind(DefaultResolvableArtifactCodec(calculatedValueContainerFactory))
    }

    fun internalTypesCodec(): Codec<Any?> = internalTypesBindings.append {
        val userTypesCodec = userTypesCodec()

        bind(TaskNodeCodec(userTypesCodec, taskNodeFactory))
        bind(DelegatingCodec<TransformStepNode>(userTypesCodec))
        bind(ActionNodeCodec(userTypesCodec))
        bind(OrdinalNodeCodec(ordinalGroupFactory))

        bind(NotImplementedCodec)
    }.build()

    private
    fun BindingsBuilder.providerTypes(
        propertyFactory: PropertyFactory,
        filePropertyFactory: FilePropertyFactory,
        nestedCodec: FixedValueReplacingProviderCodec
    ) {
        bind(ListPropertyCodec(propertyFactory, nestedCodec))
        bind(SetPropertyCodec(propertyFactory, nestedCodec))
        bind(MapPropertyCodec(propertyFactory, nestedCodec))
        bind(DirectoryPropertyCodec(filePropertyFactory, nestedCodec))
        bind(RegularFilePropertyCodec(filePropertyFactory, nestedCodec))
        bind(PropertyCodec(propertyFactory, nestedCodec))
        bind(ProviderCodec(nestedCodec))
    }

    /**
     * Returns a Codec for Provider implementations suitable for all Provider implementations.
     */
    private
    fun nestedProviderCodec(
        valueSourceProviderFactory: ValueSourceProviderFactory,
        buildStateRegistry: BuildStateRegistry,
        flowProviders: FlowProviders
    ) = FixedValueReplacingProviderCodec(
        defaultCodecForProviderWithChangingValue(
            ValueSourceProviderCodec(valueSourceProviderFactory),
            BuildServiceProviderCodec(buildStateRegistry),
            FlowProvidersCodec(flowProviders)
        )
    )

    /**
     * Returns a Codec for Provider implementations supported in the fingerprinting context. For example, BuildServiceProviders are not supported.
     */
    private
    fun nestedProviderCodecForFingerprint(
        valueSourceProviderFactory: ValueSourceProviderFactory
    ) = FixedValueReplacingProviderCodec(
        defaultCodecForProviderWithChangingValue(
            ValueSourceProviderCodec(valueSourceProviderFactory),
            UnsupportedFingerprintBuildServiceProviderCodec,
            UnsupportedFingerprintFlowProviders
        )
    )

    private
    fun BindingsBuilder.fileCollectionTypes(
        directoryFileTreeFactory: DirectoryFileTreeFactory,
        fileCollectionFactory: FileCollectionFactory,
        artifactSetConverter: ArtifactSetToFileCollectionFactory,
        fileOperations: FileOperations,
        fileFactory: FileFactory,
        patternSetFactory: Factory<PatternSet>
    ) {
        bind(DirectoryCodec(fileFactory))
        bind(RegularFileCodec(fileFactory))
        bind(ConfigurableFileTreeCodec(fileCollectionFactory))
        bind(FileTreeCodec(fileCollectionFactory, directoryFileTreeFactory, fileOperations))
        val fileCollectionCodec = FileCollectionCodec(fileCollectionFactory, artifactSetConverter)
        bind(ConfigurableFileCollectionCodec(fileCollectionCodec, fileCollectionFactory))
        bind(fileCollectionCodec)
        bind(IntersectionPatternSetCodec)
        bind(PatternSetCodec(patternSetFactory))
    }

    fun workNodeCodecFor(gradle: GradleInternal) =
        WorkNodeCodec(gradle, internalTypesCodec(), ordinalGroupFactory)
}
