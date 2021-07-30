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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.BuildIdentifierSerializer
import org.gradle.api.internal.artifacts.transform.ArtifactTransformActionScheme
import org.gradle.api.internal.artifacts.transform.ArtifactTransformParameterScheme
import org.gradle.api.internal.artifacts.transform.TransformationNode
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
import org.gradle.api.tasks.util.PatternSet
import org.gradle.composite.internal.IncludedBuildTaskGraph
import org.gradle.configurationcache.problems.DocumentationSection.NotYetImplementedJavaSerialization
import org.gradle.configurationcache.serialization.codecs.jos.JavaObjectSerializationCodec
import org.gradle.configurationcache.serialization.codecs.transform.CalculateArtifactsCodec
import org.gradle.configurationcache.serialization.codecs.transform.ChainedTransformationNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.DefaultTransformerCodec
import org.gradle.configurationcache.serialization.codecs.transform.FinalizeTransformDependenciesNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.InitialTransformationNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.IsolateTransformerParametersNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.LegacyTransformerCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformStepSpecCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformationChainCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformationStepCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformedArtifactCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformedExternalArtifactSetCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformedProjectArtifactSetCodec
import org.gradle.configurationcache.serialization.reentrant
import org.gradle.configurationcache.serialization.unsupported
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.fingerprint.InputFingerprinter
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


class Codecs(
    directoryFileTreeFactory: DirectoryFileTreeFactory,
    fileCollectionFactory: FileCollectionFactory,
    artifactSetConverter: ArtifactSetToFileCollectionFactory,
    fileLookup: FileLookup,
    propertyFactory: PropertyFactory,
    filePropertyFactory: FilePropertyFactory,
    fileResolver: FileResolver,
    instantiator: Instantiator,
    listenerManager: ListenerManager,
    taskNodeFactory: TaskNodeFactory,
    inputFingerprinter: InputFingerprinter,
    buildOperationExecutor: BuildOperationExecutor,
    classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    isolatableFactory: IsolatableFactory,
    managedFactoryRegistry: ManagedFactoryRegistry,
    parameterScheme: ArtifactTransformParameterScheme,
    actionScheme: ArtifactTransformActionScheme,
    attributesFactory: ImmutableAttributesFactory,
    valueSourceProviderFactory: ValueSourceProviderFactory,
    calculatedValueContainerFactory: CalculatedValueContainerFactory,
    patternSetFactory: Factory<PatternSet>,
    fileOperations: FileOperations,
    fileFactory: FileFactory,
    includedTaskGraph: IncludedBuildTaskGraph,
    buildStateRegistry: BuildStateRegistry,
    documentationRegistry: DocumentationRegistry,
) {

    val userTypesCodec = BindingsBackedCodec {

        unsupportedTypes()

        baseTypes()

        bind(HASHCODE_SERIALIZER)
        bind(BrokenValueCodec)

        providerTypes(
            propertyFactory,
            filePropertyFactory,
            valueSourceProviderFactory,
            buildStateRegistry
        )

        bind(ListenerBroadcastCodec(listenerManager))
        bind(LoggerCodec)

        fileCollectionTypes(directoryFileTreeFactory, fileCollectionFactory, artifactSetConverter, fileOperations, fileFactory, patternSetFactory)

        bind(ApiTextResourceAdapterCodec)

        bind(ClosureCodec)
        bind(GroovyMetaClassCodec)

        // Dependency management types
        bind(ArtifactCollectionCodec(fileCollectionFactory, artifactSetConverter))
        bind(ImmutableAttributesCodec(attributesFactory, managedFactoryRegistry))
        bind(AttributeContainerCodec(attributesFactory, managedFactoryRegistry))
        bind(InitialTransformationNodeCodec(buildOperationExecutor, calculatedValueContainerFactory))
        bind(ChainedTransformationNodeCodec(buildOperationExecutor, calculatedValueContainerFactory))
        bind(TransformationStepCodec(inputFingerprinter))
        bind(TransformationChainCodec())
        bind(DefaultTransformerCodec(fileLookup, actionScheme))
        bind(LegacyTransformerCodec(actionScheme))
        bind(DefaultResolvableArtifactCodec(calculatedValueContainerFactory))
        bind(TransformStepSpecCodec)
        bind(PublishArtifactLocalArtifactMetadataCodec)
        bind(TransformedProjectArtifactSetCodec())
        bind(TransformedExternalArtifactSetCodec())
        bind(CalculateArtifactsCodec(calculatedValueContainerFactory))
        bind(TransformedArtifactCodec(calculatedValueContainerFactory))
        bind(LocalFileDependencyBackedArtifactSetCodec(instantiator, attributesFactory, fileCollectionFactory, calculatedValueContainerFactory))
        bind(CalculatedValueContainerCodec(calculatedValueContainerFactory))
        bind(IsolateTransformerParametersNodeCodec(parameterScheme, isolatableFactory, buildOperationExecutor, classLoaderHierarchyHasher, fileCollectionFactory, documentationRegistry))
        bind(FinalizeTransformDependenciesNodeCodec())
        bind(WorkNodeActionCodec)
        bind(CapabilitiesCodec)

        bind(DefaultCopySpecCodec(patternSetFactory, fileCollectionFactory, instantiator))
        bind(DestinationRootCopySpecCodec(fileResolver))

        bind(TaskReferenceCodec)

        bind(IsolatedManagedValueCodec(managedFactoryRegistry))
        bind(IsolatedImmutableManagedValueCodec(managedFactoryRegistry))
        bind(IsolatedSerializedValueSnapshotCodec)
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
        bind(JavaObjectSerializationCodec())

        bind(BeanSpecCodec)

        // This protects the BeanCodec against StackOverflowErrors but
        // we can still get them for the other codecs, for instance,
        // with deeply nested Lists, deeply nested Maps, etc.
        bind(reentrant(BeanCodec()))
    }

    val internalTypesCodec = BindingsBackedCodec {
        baseTypes()

        providerTypes(propertyFactory, filePropertyFactory, valueSourceProviderFactory, buildStateRegistry)
        fileCollectionTypes(directoryFileTreeFactory, fileCollectionFactory, artifactSetConverter, fileOperations, fileFactory, patternSetFactory)

        bind(BuildIdentifierSerializer())
        bind(TaskNodeCodec(userTypesCodec, taskNodeFactory))
        bind(TaskInAnotherBuildCodec(includedTaskGraph))
        bind(DelegatingCodec<TransformationNode>(userTypesCodec))
        bind(ActionNodeCodec(userTypesCodec))

        bind(DefaultResolvableArtifactCodec(calculatedValueContainerFactory))

        bind(NotImplementedCodec)
    }

    private
    fun BindingsBuilder.providerTypes(
        propertyFactory: PropertyFactory,
        filePropertyFactory: FilePropertyFactory,
        valueSourceProviderFactory: ValueSourceProviderFactory,
        buildStateRegistry: BuildStateRegistry
    ) {
        val nestedCodec = FixedValueReplacingProviderCodec(valueSourceProviderFactory, buildStateRegistry)
        bind(ListPropertyCodec(propertyFactory, nestedCodec))
        bind(SetPropertyCodec(propertyFactory, nestedCodec))
        bind(MapPropertyCodec(propertyFactory, nestedCodec))
        bind(DirectoryPropertyCodec(filePropertyFactory, nestedCodec))
        bind(RegularFilePropertyCodec(filePropertyFactory, nestedCodec))
        bind(PropertyCodec(propertyFactory, nestedCodec))
        bind(ProviderCodec(nestedCodec))
    }

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
        bind(IntersectPatternSetCodec)
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
        bind(ImmutableListCodec)

        // Only serialize certain Set implementations for now, as some custom types extend Set (eg DomainObjectContainer)
        bind(hashSetCodec)
        bind(treeSetCodec)
        bind(ImmutableSetCodec)

        // Only serialize certain Map implementations for now, as some custom types extend Map (eg DefaultManifest)
        bind(linkedHashMapCodec)
        bind(hashMapCodec)
        bind(treeMapCodec)
        bind(concurrentHashMapCodec)
        bind(ImmutableMapCodec)

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

        bind(EnumCodec)
        bind(RegexpPatternCodec)
        bind(UrlCodec)

        javaTimeTypes()
    }
}
