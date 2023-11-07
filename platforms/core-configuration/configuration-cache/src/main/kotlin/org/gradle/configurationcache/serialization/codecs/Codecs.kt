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

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.flow.FlowProviders
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.BuildIdentifierSerializer
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
import org.gradle.configurationcache.problems.DocumentationSection.NotYetImplementedJavaSerialization
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.codecs.jos.JavaObjectSerializationCodec
import org.gradle.configurationcache.serialization.codecs.jos.JavaSerializationEncodingLookup
import org.gradle.configurationcache.serialization.codecs.transform.CalculateArtifactsCodec
import org.gradle.configurationcache.serialization.codecs.transform.ChainedTransformStepNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.ComponentVariantIdentifierCodec
import org.gradle.configurationcache.serialization.codecs.transform.DefaultTransformCodec
import org.gradle.configurationcache.serialization.codecs.transform.FinalizeTransformDependenciesNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.InitialTransformStepNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.IsolateTransformParametersCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformChainCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformStepCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformStepSpecCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformedArtifactCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformedExternalArtifactSetCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformedProjectArtifactSetCodec
import org.gradle.configurationcache.serialization.reentrant
import org.gradle.configurationcache.serialization.unsupported
import org.gradle.execution.plan.OrdinalGroupFactory
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory.BIG_DECIMAL_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BIG_INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BOOLEAN_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BYTE_ARRAY_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BYTE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.CHAR_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.DOUBLE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FLOAT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.HASHCODE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.LONG_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.PATH_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.SHORT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER
import org.gradle.internal.state.ManagedFactoryRegistry
import java.io.Externalizable


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
    buildOperationExecutor: BuildOperationExecutor,
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

            bind(ClosureCodec)
            bind(GroovyMetaClassCodec)
            bind(SerializedLambdaParametersCheckingCodec)

            // Dependency management types
            bind(ArtifactCollectionCodec(calculatedValueContainerFactory, artifactSetConverter))
            bind(ImmutableAttributesCodec(attributesFactory, managedFactoryRegistry))
            bind(AttributeContainerCodec(attributesFactory, managedFactoryRegistry))
            bind(ComponentVariantIdentifierCodec)
            bind(InitialTransformStepNodeCodec(transformStepNodeFactory, buildOperationExecutor, calculatedValueContainerFactory))
            bind(ChainedTransformStepNodeCodec(transformStepNodeFactory, buildOperationExecutor, calculatedValueContainerFactory))
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
            bind(IsolateTransformParametersCodec(parameterScheme, isolatableFactory, buildOperationExecutor, classLoaderHierarchyHasher, fileCollectionFactory, documentationRegistry))
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

            bind(ServicesCodec())

            bind(ProxyCodec)

            // Java serialization integration
            bind(unsupported<Externalizable>(NotYetImplementedJavaSerialization))

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
        ValueSourceProviderCodec(valueSourceProviderFactory),
        BuildServiceProviderCodec(buildStateRegistry),
        FlowProvidersCodec(flowProviders)
    )

    /**
     * Returns a Codec for Provider implementations supported in the fingerprinting context. For example, BuildServiceProviders are not supported.
     */
    private
    fun nestedProviderCodecForFingerprint(
        valueSourceProviderFactory: ValueSourceProviderFactory
    ) = FixedValueReplacingProviderCodec(
        ValueSourceProviderCodec(valueSourceProviderFactory),
        UnsupportedFingerprintBuildServiceProviderCodec,
        UnsupportedFingerprintFlowProviders
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

    private
    fun BindingsBuilder.baseTypes() {
        bind(STRING_SERIALIZER)
        bind(BOOLEAN_SERIALIZER)
        bind(INTEGER_SERIALIZER)
        bind(CHAR_SERIALIZER)
        bind(SHORT_SERIALIZER)
        bind(LONG_SERIALIZER)
        bind(BYTE_SERIALIZER)
        bind(FLOAT_SERIALIZER)
        bind(DOUBLE_SERIALIZER)
        bind(FILE_SERIALIZER)
        bind(PATH_SERIALIZER)
        bind(BIG_INTEGER_SERIALIZER)
        bind(BIG_DECIMAL_SERIALIZER)
        bind(ClassCodec)
        bind(MethodCodec)

        // Only serialize certain List implementations
        bind(arrayListCodec)
        bind(linkedListCodec)
        bind(copyOnWriteArrayListCodec)
        bind(ImmutableListCodec)

        // Only serialize certain Set implementations for now, as some custom types extend Set (e.g. DomainObjectContainer)
        bind(HashSetCodec)
        bind(treeSetCodec)
        bind(copyOnWriteArraySetCodec)
        bind(ImmutableSetCodec)

        // Only serialize certain Map implementations for now, as some custom types extend Map (e.g. DefaultManifest)
        bind(linkedHashMapCodec)
        bind(hashMapCodec)
        bind(treeMapCodec)
        bind(concurrentHashMapCodec)
        bind(ImmutableMapCodec)
        bind(propertiesCodec)
        bind(hashtableCodec)

        // Arrays
        bind(BYTE_ARRAY_SERIALIZER)
        bind(ShortArrayCodec)
        bind(IntArrayCodec)
        bind(LongArrayCodec)
        bind(FloatArrayCodec)
        bind(DoubleArrayCodec)
        bind(BooleanArrayCodec)
        bind(CharArrayCodec)
        bind(NonPrimitiveArrayCodec)

        // Only serialize certain Queue implementations
        bind(arrayDequeCodec)

        bind(EnumCodec)
        bind(RegexpPatternCodec)
        bind(UrlCodec)
        bind(LevelCodec)
        bind(UnitCodec)
        bind(CharsetCodec)

        javaTimeTypes()

        bind(BuildIdentifierSerializer())
    }

    fun workNodeCodecFor(gradle: GradleInternal) =
        WorkNodeCodec(gradle, internalTypesCodec(), ordinalGroupFactory)
}
