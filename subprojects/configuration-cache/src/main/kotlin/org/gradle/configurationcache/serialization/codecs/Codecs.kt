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

import org.gradle.api.internal.artifacts.transform.ArtifactTransformActionScheme
import org.gradle.api.internal.artifacts.transform.ArtifactTransformListener
import org.gradle.api.internal.artifacts.transform.ArtifactTransformParameterScheme
import org.gradle.api.internal.artifacts.transform.TransformationNodeRegistry
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
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.tasks.util.PatternSet
import org.gradle.configurationcache.problems.DocumentationSection.NotYetImplementedJavaSerialization
import org.gradle.configurationcache.serialization.codecs.jos.JavaObjectSerializationCodec
import org.gradle.configurationcache.serialization.codecs.transform.ChainedTransformationNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.DefaultTransformerCodec
import org.gradle.configurationcache.serialization.codecs.transform.InitialTransformationNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.IsolateTransformerParametersNodeCodec
import org.gradle.configurationcache.serialization.codecs.transform.LegacyTransformerCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformDependenciesCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformationChainCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformationNodeReferenceCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformationStepCodec
import org.gradle.configurationcache.serialization.codecs.transform.TransformedExternalArtifactSetCodec
import org.gradle.configurationcache.serialization.reentrant
import org.gradle.configurationcache.serialization.unsupported
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.internal.Factory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory.BIG_DECIMAL_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BIG_INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BOOLEAN_SERIALIZER
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
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.state.ManagedFactoryRegistry
import java.io.Externalizable


class Codecs(
    directoryFileTreeFactory: DirectoryFileTreeFactory,
    fileCollectionFactory: FileCollectionFactory,
    fileLookup: FileLookup,
    propertyFactory: PropertyFactory,
    filePropertyFactory: FilePropertyFactory,
    fileResolver: FileResolver,
    instantiator: Instantiator,
    listenerManager: ListenerManager,
    taskNodeFactory: TaskNodeFactory,
    fingerprinterRegistry: FileCollectionFingerprinterRegistry,
    buildOperationExecutor: BuildOperationExecutor,
    classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    isolatableFactory: IsolatableFactory,
    valueSnapshotter: ValueSnapshotter,
    buildServiceRegistry: BuildServiceRegistryInternal,
    managedFactoryRegistry: ManagedFactoryRegistry,
    parameterScheme: ArtifactTransformParameterScheme,
    actionScheme: ArtifactTransformActionScheme,
    attributesFactory: ImmutableAttributesFactory,
    transformListener: ArtifactTransformListener,
    transformationNodeRegistry: TransformationNodeRegistry,
    valueSourceProviderFactory: ValueSourceProviderFactory,
    patternSetFactory: Factory<PatternSet>,
    fileOperations: FileOperations,
    fileFactory: FileFactory
) {

    val userTypesCodec = BindingsBackedCodec {

        unsupportedTypes()

        baseTypes()

        bind(HASHCODE_SERIALIZER)
        bind(BrokenValueCodec)

        providerTypes(propertyFactory, filePropertyFactory, buildServiceRegistry, valueSourceProviderFactory)

        bind(ListenerBroadcastCodec(listenerManager))
        bind(LoggerCodec)

        fileCollectionTypes(directoryFileTreeFactory, fileCollectionFactory, fileOperations, fileFactory, patternSetFactory)

        bind(ApiTextResourceAdapterCodec)

        bind(ClosureCodec)
        bind(GroovyMetaClassCodec)

        // Dependency management types
        bind(ArtifactCollectionCodec(fileCollectionFactory))
        bind(ImmutableAttributesCodec(attributesFactory, managedFactoryRegistry))
        bind(AttributeContainerCodec(attributesFactory, managedFactoryRegistry))
        bind(TransformationNodeReferenceCodec)
        bind(TransformationStepCodec(fingerprinterRegistry))
        bind(TransformationChainCodec())
        bind(DefaultTransformerCodec(buildOperationExecutor, classLoaderHierarchyHasher, isolatableFactory, valueSnapshotter, fileCollectionFactory, fileLookup, parameterScheme, actionScheme))
        bind(LegacyTransformerCodec(actionScheme))
        bind(ResolvableArtifactCodec)
        bind(TransformDependenciesCodec)
        bind(PublishArtifactLocalArtifactMetadataCodec)
        bind(TransformedExternalArtifactSetCodec(transformationNodeRegistry))

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

        // This protects the BeanCodec against StackOverflowErrors but
        // we can still get them for the other codecs, for instance,
        // with deeply nested Lists, deeply nested Maps, etc.
        bind(reentrant(BeanCodec()))
    }

    val internalTypesCodec = BindingsBackedCodec {
        baseTypes()

        providerTypes(propertyFactory, filePropertyFactory, buildServiceRegistry, valueSourceProviderFactory)
        fileCollectionTypes(directoryFileTreeFactory, fileCollectionFactory, fileOperations, fileFactory, patternSetFactory)

        bind(TaskNodeCodec(userTypesCodec, taskNodeFactory))
        bind(InitialTransformationNodeCodec(userTypesCodec, buildOperationExecutor, transformListener))
        bind(ChainedTransformationNodeCodec(userTypesCodec, buildOperationExecutor, transformListener))
        bind(IsolateTransformerParametersNodeCodec(userTypesCodec))
        bind(WorkNodeActionCodec)
        bind(ActionNodeCodec)

        bind(ResolvableArtifactCodec)
        bind(TransformDependenciesCodec)

        bind(NotImplementedCodec)
    }

    private
    fun BindingsBuilder.providerTypes(propertyFactory: PropertyFactory, filePropertyFactory: FilePropertyFactory, buildServiceRegistry: BuildServiceRegistryInternal, valueSourceProviderFactory: ValueSourceProviderFactory) {
        val nestedCodec = FixedValueReplacingProviderCodec(valueSourceProviderFactory, buildServiceRegistry)
        bind(ListPropertyCodec(propertyFactory, nestedCodec))
        bind(SetPropertyCodec(propertyFactory, nestedCodec))
        bind(MapPropertyCodec(propertyFactory, nestedCodec))
        bind(DirectoryPropertyCodec(filePropertyFactory, nestedCodec))
        bind(RegularFilePropertyCodec(filePropertyFactory, nestedCodec))
        bind(PropertyCodec(propertyFactory, nestedCodec))
        bind(ProviderCodec(nestedCodec))
    }

    private
    fun BindingsBuilder.fileCollectionTypes(directoryFileTreeFactory: DirectoryFileTreeFactory, fileCollectionFactory: FileCollectionFactory, fileOperations: FileOperations, fileFactory: FileFactory, patternSetFactory: Factory<PatternSet>) {
        bind(DirectoryCodec(fileFactory))
        bind(RegularFileCodec(fileFactory))
        bind(ConfigurableFileTreeCodec(fileCollectionFactory))
        bind(FileTreeCodec(fileCollectionFactory, directoryFileTreeFactory, fileOperations))
        val fileCollectionCodec = FileCollectionCodec(fileCollectionFactory)
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

        bind(arrayCodec)
        bind(EnumCodec)
        bind(RegexpPatternCodec)

        javaTimeTypes()
    }
}
