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

import org.gradle.api.Project
import org.gradle.api.Script
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.transform.ArtifactTransformActionScheme
import org.gradle.api.internal.artifacts.transform.ArtifactTransformListener
import org.gradle.api.internal.artifacts.transform.ArtifactTransformParameterScheme
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.util.internal.PatternSpecFactory
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.instantexecution.serialization.ownerService
import org.gradle.instantexecution.serialization.reentrant
import org.gradle.instantexecution.serialization.unsupported
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory.BOOLEAN_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BYTE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.CHAR_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.DOUBLE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FLOAT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.LONG_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.PATH_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.SHORT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.kotlin.dsl.*
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecActionFactory
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.workers.WorkerExecutor


class Codecs(
    directoryFileTreeFactory: DirectoryFileTreeFactory,
    fileCollectionFactory: FileCollectionFactory,
    fileLookup: FileLookup,
    filePropertyFactory: FilePropertyFactory,
    fileResolver: FileResolver,
    instantiator: Instantiator,
    listenerManager: ListenerManager,
    projectStateRegistry: ProjectStateRegistry,
    taskNodeFactory: TaskNodeFactory,
    fingerprinterRegistry: FileCollectionFingerprinterRegistry,
    projectFinder: ProjectFinder,
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
    valueSourceProviderFactory: ValueSourceProviderFactory
) {

    val userTypesCodec = BindingsBackedCodec {

        bind(unsupported<Project>())
        bind(unsupported<Gradle>())
        bind(unsupported<Settings>())
        bind(unsupported<TaskContainer>())
        bind(unsupported<ConfigurationContainer>())
        bind(unsupported<KotlinScript>())
        bind(unsupported<Script>())

        baseTypes()

        bind(HashCodeSerializer())
        bind(BrokenValueCodec)

        providerTypes(filePropertyFactory, buildServiceRegistry, valueSourceProviderFactory)

        bind(ListenerBroadcastCodec(listenerManager))
        bind(LoggerCodec)

        fileCollectionTypes(directoryFileTreeFactory, fileCollectionFactory)

        bind(ClosureCodec)
        bind(GroovyMetaClassCodec)

        // Dependency management types
        bind(ArtifactCollectionCodec)
        bind(AttributeContainerCodec(attributesFactory))
        bind(TransformationNodeReferenceCodec)

        bind(DefaultCopySpecCodec(fileResolver, fileCollectionFactory, instantiator))
        bind(DestinationRootCopySpecCodec(fileResolver))

        bind(TaskReferenceCodec)

        bind(ownerService<ProviderFactory>())
        bind(ownerService<ObjectFactory>())
        bind(ownerService<WorkerExecutor>())
        bind(ownerService<ProjectLayout>())
        bind(ownerService<PatternSpecFactory>())
        bind(ownerService<FileResolver>())
        bind(ownerService<Instantiator>())
        bind(ownerService<FileCollectionFactory>())
        bind(ownerService<FileSystemOperations>())
        bind(ownerService<FileOperations>())
        bind(ownerService<BuildOperationExecutor>())
        bind(ownerService<ToolingModelBuilderRegistry>())
        bind(ownerService<ExecOperations>())
        bind(ownerService<ExecActionFactory>())
        bind(ownerService<BuildOperationListenerManager>())
        bind(ownerService<BuildRequestMetaData>())
        bind(ownerService<ListenerManager>())

        bind(ProxyCodec)

        bind(SerializableWriteObjectCodec())
        bind(SerializableWriteReplaceCodec())

        // This protects the BeanCodec against StackOverflowErrors but
        // we can still get them for the other codecs, for instance,
        // with deeply nested Lists, deeply nested Maps, etc.
        bind(reentrant(BeanCodec()))
    }

    val internalTypesCodec = BindingsBackedCodec {

        baseTypes()

        providerTypes(filePropertyFactory, buildServiceRegistry, valueSourceProviderFactory)
        fileCollectionTypes(directoryFileTreeFactory, fileCollectionFactory)

        bind(TaskNodeCodec(projectStateRegistry, userTypesCodec, taskNodeFactory))
        bind(InitialTransformationNodeCodec(buildOperationExecutor, transformListener))
        bind(ChainedTransformationNodeCodec(buildOperationExecutor, transformListener))
        bind(ActionNodeCodec)
        bind(ResolvableArtifactCodec)
        bind(TransformationStepCodec(projectStateRegistry, fingerprinterRegistry, projectFinder))
        bind(DefaultTransformerCodec(userTypesCodec, buildOperationExecutor, classLoaderHierarchyHasher, isolatableFactory, valueSnapshotter, fileCollectionFactory, fileLookup, parameterScheme, actionScheme))
        bind(LegacyTransformerCodec(actionScheme))

        bind(IsolatedManagedValueCodec(managedFactoryRegistry))
        bind(IsolatedImmutableManagedValueCodec(managedFactoryRegistry))
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

        bind(NotImplementedCodec)
    }

    private
    fun BindingsBuilder.providerTypes(filePropertyFactory: FilePropertyFactory, buildServiceRegistry: BuildServiceRegistryInternal, valueSourceProviderFactory: ValueSourceProviderFactory) {
        bind(ListPropertyCodec)
        bind(SetPropertyCodec)
        bind(MapPropertyCodec)
        bind(DirectoryPropertyCodec(filePropertyFactory))
        bind(RegularFilePropertyCodec(filePropertyFactory))
        bind(PropertyCodec)
        bind(BuildServiceProviderCodec(buildServiceRegistry))
        bind(ValueSourceProviderCodec(valueSourceProviderFactory))
        bind(ProviderCodec)
    }

    private
    fun BindingsBuilder.fileCollectionTypes(directoryFileTreeFactory: DirectoryFileTreeFactory, fileCollectionFactory: FileCollectionFactory) {
        bind(FileTreeCodec(directoryFileTreeFactory))
        bind(ConfigurableFileCollectionCodec(fileCollectionFactory))
        bind(FileCollectionCodec(fileCollectionFactory))
        bind(PatternSetCodec)
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
        bind(ClassCodec)
        bind(MethodCodec)

        // Only serialize certain List implementations
        bind(arrayListCodec)
        bind(linkedListCodec)
        bind(ImmutableListCodec)

        // Only serialize certain Set implementations for now, as some custom types extend Set (eg DomainObjectContainer)
        bind(linkedHashSetCodec)
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
    }
}
