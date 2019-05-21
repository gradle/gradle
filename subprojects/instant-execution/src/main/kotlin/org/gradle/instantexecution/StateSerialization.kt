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

package org.gradle.instantexecution

import groovy.lang.GroovyObjectSupport
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.file.DefaultCompositeFileTree
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionLeafVisitor
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.tasks.util.internal.PatternSpecFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SetSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sun.reflect.ReflectionFactory
import java.io.File
import kotlin.reflect.KClass


class StateSerialization(
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val fileCollectionFactory: FileCollectionFactory,
    private val fileResolver: FileResolver,
    private val instantiator: Instantiator,
    private val objectFactory: ObjectFactory,
    private val patternSpecFactory: PatternSpecFactory,
    private val filePropertyFactory: FilePropertyFactory
) {

    private
    val fileSetSerializer = SetSerializer(BaseSerializerFactory.FILE_SERIALIZER)

    private
    val stringSerializer = BaseSerializerFactory.STRING_SERIALIZER

    private
    val integerSerializer = BaseSerializerFactory.INTEGER_SERIALIZER

    private
    val shortSerializer = BaseSerializerFactory.SHORT_SERIALIZER

    private
    val longSerializer = BaseSerializerFactory.LONG_SERIALIZER

    private
    val byteSerializer = BaseSerializerFactory.BYTE_SERIALIZER

    private
    val floatSerializer = BaseSerializerFactory.FLOAT_SERIALIZER

    private
    val doubleSerializer = BaseSerializerFactory.DOUBLE_SERIALIZER

    private
    val booleanSerializer = BaseSerializerFactory.BOOLEAN_SERIALIZER

    private
    val fileTreeSerializer = FileTreeSerializer()

    fun newSerializer(): StateSerializer =
        DefaultStateSerializer()

    fun deserializerFor(beanClassLoader: ClassLoader): StateDeserializer =
        DefaultStateDeserializer(beanClassLoader)

    private
    inner class DefaultStateSerializer : StateSerializer {

        override fun serializerFor(value: Any?): ValueSerializer? = when (value) {
            null -> { encoder, _ ->
                encoder.writeByte(NULL_VALUE)
            }
            is String -> { encoder, _ ->
                encoder.writeWithTag(STRING_TYPE, stringSerializer, value)
            }
            is Boolean -> { encoder, _ ->
                encoder.writeWithTag(BOOLEAN_TYPE, booleanSerializer, value)
            }
            is Int -> { encoder, _ ->
                encoder.writeWithTag(INT_TYPE, integerSerializer, value)
            }
            is Short -> { encoder, _ ->
                encoder.writeWithTag(SHORT_TYPE, shortSerializer, value)
            }
            is Long -> { encoder, _ ->
                encoder.writeWithTag(LONG_TYPE, longSerializer, value)
            }
            is Byte -> { encoder, _ ->
                encoder.writeWithTag(BYTE_TYPE, byteSerializer, value)
            }
            is Float -> { encoder, _ ->
                encoder.writeWithTag(FLOAT_TYPE, floatSerializer, value)
            }
            is Double -> { encoder, _ ->
                encoder.writeWithTag(DOUBLE_TYPE, doubleSerializer, value)
            }
            is FileTreeInternal -> { encoder, _ ->
                encoder.writeWithTag(FILE_TREE_TYPE, fileTreeSerializer, value)
            }
            is File -> { encoder, _ ->
                encoder.writeWithTag(FILE_TYPE, BaseSerializerFactory.FILE_SERIALIZER, value)
            }
            is Class<*> -> { encoder, _ ->
                encoder.writeByte(CLASS_TYPE)
                encoder.writeString(value.name)
            }
            is List<*> -> collectionSerializerFor(LIST_TYPE, value)
            is Set<*> -> collectionSerializerFor(SET_TYPE, value)
            // Only serialize certain Map implementations for now, as some custom types extend Map (eg DefaultManifest)
            is HashMap<*, *> -> mapSerializerFor(value)
            is LinkedHashMap<*, *> -> mapSerializerFor(value)
            is Logger -> { encoder, _ ->
                encoder.writeByte(LOGGER_TYPE)
                encoder.writeString(value.name)
            }
            is FileCollection -> { encoder, _ ->
                encoder.writeWithTag(FILE_COLLECTION_TYPE, fileSetSerializer, value.files)
            }
            is ArtifactCollection -> { encoder, _ ->
                encoder.writeByte(ARTIFACT_COLLECTION_TYPE)
            }
            is ObjectFactory -> { encoder, _ ->
                encoder.writeByte(OBJECT_FACTORY_TYPE)
            }
            is PatternSpecFactory -> { encoder, _ ->
                encoder.writeByte(PATTERN_SPEC_FACTORY_TYPE)
            }
            is FileResolver -> { encoder, _ ->
                encoder.writeByte(FILE_RESOLVER_TYPE)
            }
            is Instantiator -> { encoder, _ ->
                encoder.writeByte(INSTANTIATOR_TYPE)
            }
            is FileCollectionFactory -> { encoder, _ ->
                encoder.writeByte(FILE_COLLECTION_FACTORY_TYPE)
            }
            is DefaultCopySpec -> defaultCopySpecSerializerFor(value)
            is DestinationRootCopySpec -> destinationRootCopySpecSerializerFor(value)
            is Project -> projectStateType(Project::class)
            is Gradle -> projectStateType(Gradle::class)
            is Settings -> projectStateType(Settings::class)
            is Task -> { encoder, context ->
                if (value == context.owner) {
                    encoder.writeByte(THIS_TASK)
                } else {
                    projectStateType(Task::class)
                    encoder.writeByte(NULL_VALUE)
                }
            }
            is FileOperations -> { encoder, _ ->
                encoder.writeByte(FILE_OPERATIONS_TYPE)
            }
            else -> beanSerializerFor(value)
        }

        private
        fun projectStateType(type: KClass<*>): ValueSerializer? {
            LoggerFactory.getLogger(StateSerialization::class.java).warn("instant-execution > Cannot serialize object of type ${type.java.name} as these are not supported with instant execution.")
            return null
        }

        private
        fun defaultCopySpecSerializerFor(value: DefaultCopySpec): ValueSerializer = { encoder, context ->
            val allSourcePaths = ArrayList<File>()
            collectSourcePathsFrom(value, allSourcePaths)
            encoder.writeByte(DEFAULT_COPY_SPEC)
            write(encoder, context, allSourcePaths)
        }

        private
        fun destinationRootCopySpecSerializerFor(value: DestinationRootCopySpec): ValueSerializer = { encoder, context ->
            encoder.writeByte(DESTINATION_ROOT_COPY_SPEC)
            write(encoder, context, value.destinationDir)
            write(encoder, context, value.delegate)
        }

        private
        fun beanSerializerFor(value: Any): ValueSerializer = { encoder, context ->
            encoder.writeByte(BEAN)
            val id = context.getId(value)
            if (id != null) {
                encoder.writeSmallInt(id)
            } else {
                encoder.writeSmallInt(context.putInstance(value))
                val beanType = GeneratedSubclasses.unpackType(value)
                encoder.writeString(beanType.name)
                BeanFieldSerializer(value, beanType, this).invoke(encoder, context)
            }
        }

        private
        fun collectionSerializerFor(tag: Byte, value: Collection<*>): ValueSerializer = { encoder, context ->
            encoder.writeByte(tag)
            encoder.writeSmallInt(value.size)
            for (item in value) {
                write(encoder, context, item)
            }
        }

        private
        fun mapSerializerFor(value: Map<*, *>): ValueSerializer = { encoder, context ->
            encoder.writeByte(MAP_TYPE)
            encoder.writeSmallInt(value.size)
            for (entry in value.entries) {
                write(encoder, context, entry.key)
                write(encoder, context, entry.value)
            }
        }

        private
        fun write(encoder: Encoder, context: SerializationContext, value: Any?) {
            serializerFor(value)!!.invoke(encoder, context)
        }
    }

    private
    inner class DefaultStateDeserializer(
        private val beanClassLoader: ClassLoader
    ) : StateDeserializer {

        override fun read(decoder: Decoder, context: DeserializationContext): Any? = when (decoder.readByte()) {
            NULL_VALUE -> null
            STRING_TYPE -> stringSerializer.read(decoder)
            BOOLEAN_TYPE -> booleanSerializer.read(decoder)
            INT_TYPE -> integerSerializer.read(decoder)
            SHORT_TYPE -> shortSerializer.read(decoder)
            LONG_TYPE -> longSerializer.read(decoder)
            BYTE_TYPE -> byteSerializer.read(decoder)
            DOUBLE_TYPE -> doubleSerializer.read(decoder)
            FLOAT_TYPE -> floatSerializer.read(decoder)
            FILE_TYPE -> BaseSerializerFactory.FILE_SERIALIZER.read(decoder)
            CLASS_TYPE -> beanClassLoader.loadClass(decoder.readString())
            LIST_TYPE -> deserializeCollection(decoder, context) { ArrayList<Any?>(it) }
            SET_TYPE -> deserializeCollection(decoder, context) { LinkedHashSet<Any?>(it) }
            MAP_TYPE -> deserializeMap(decoder, context)
            LOGGER_TYPE -> {
                LoggerFactory.getLogger(decoder.readString())
            }
            THIS_TASK -> context.owner
            FILE_TREE_TYPE -> fileTreeSerializer.read(decoder)
            FILE_COLLECTION_TYPE -> fileCollectionFactory.fixed(fileSetSerializer.read(decoder))
            ARTIFACT_COLLECTION_TYPE -> EmptyArtifactCollection(ImmutableFileCollection.of())
            OBJECT_FACTORY_TYPE -> objectFactory
            FILE_RESOLVER_TYPE -> fileResolver
            INSTANTIATOR_TYPE -> instantiator
            PATTERN_SPEC_FACTORY_TYPE -> patternSpecFactory
            FILE_COLLECTION_FACTORY_TYPE -> fileCollectionFactory
            FILE_OPERATIONS_TYPE -> (context.owner.project as ProjectInternal).services.get(FileOperations::class.java)
            DEFAULT_COPY_SPEC -> deserializeDefaultCopySpec(decoder, context)
            DESTINATION_ROOT_COPY_SPEC -> deserializeDestinationRootCopySpec(decoder, context)
            BEAN -> deserializeBean(decoder, context, beanClassLoader)
            else -> throw UnsupportedOperationException()
        }

        private
        fun deserializeBean(decoder: Decoder, context: DeserializationContext, loader: ClassLoader): Any {
            val id = decoder.readSmallInt()
            val previousValue = context.getInstance(id)
            if (previousValue != null) {
                return previousValue
            }
            val beanTypeName = decoder.readString()
            val beanType = loader.loadClass(beanTypeName)
            val constructor = if (GroovyObjectSupport::class.java.isAssignableFrom(beanType)) {
                // Run the `GroovyObjectSupport` constructor, to initialize the metadata field
                ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, GroovyObjectSupport::class.java.getConstructor())
            } else {
                ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, Object::class.java.getConstructor())
            }
            val bean = constructor.newInstance()
            context.putInstance(id, bean)
            BeanFieldDeserializer(bean, bean.javaClass, this, filePropertyFactory).deserialize(decoder, context)
            return bean
        }

        private
        fun <T : MutableCollection<Any?>> deserializeCollection(decoder: Decoder, context: DeserializationContext, factory: (Int) -> T): T {
            val size = decoder.readSmallInt()
            val items = factory(size)
            for (i in 0 until size) {
                items.add(read(decoder, context))
            }
            return items
        }

        private
        fun deserializeMap(decoder: Decoder, context: DeserializationContext): Map<Any?, Any?> {
            val size = decoder.readSmallInt()
            val items = LinkedHashMap<Any?, Any?>()
            for (i in 0 until size) {
                val key = read(decoder, context)
                val value = read(decoder, context)
                items[key] = value
            }
            return items
        }

        private
        fun deserializeDestinationRootCopySpec(decoder: Decoder, context: DeserializationContext): DestinationRootCopySpec {
            val destDir = read(decoder, context) as? File
            val delegate = read(decoder, context) as CopySpecInternal
            val spec = DestinationRootCopySpec(fileResolver, delegate)
            destDir?.let(spec::into)
            return spec
        }

        private
        fun deserializeDefaultCopySpec(decoder: Decoder, context: DeserializationContext): DefaultCopySpec {
            @Suppress("unchecked_cast")
            val sourceFiles = read(decoder, context) as List<File>
            val copySpec = DefaultCopySpec(fileResolver, instantiator)
            copySpec.from(sourceFiles)
            return copySpec
        }
    }

    private
    inner class FileTreeSerializer : Serializer<FileTreeInternal> {

        override fun read(decoder: Decoder): FileTreeInternal =
            DefaultCompositeFileTree(
                fileSetSerializer.read(decoder).map {
                    FileTreeAdapter(directoryFileTreeFactory.create(it))
                }
            )

        override fun write(encoder: Encoder, value: FileTreeInternal) {
            val visitor = FileTreeVisitor()
            value.visitLeafCollections(visitor)
            fileSetSerializer.write(encoder, visitor.roots)
        }
    }

    private
    class FileTreeVisitor : FileCollectionLeafVisitor {

        internal
        var roots = LinkedHashSet<File>()

        override fun visitCollection(fileCollection: FileCollectionInternal) = throw UnsupportedOperationException()

        override fun visitGenericFileTree(fileTree: FileTreeInternal) = throw UnsupportedOperationException()

        override fun visitFileTree(root: File, patterns: PatternSet) {
            roots.add(root)
        }
    }

    companion object {
        // JVM types
        const val NULL_VALUE: Byte = 0
        const val STRING_TYPE: Byte = 1
        const val BOOLEAN_TYPE: Byte = 2
        const val BYTE_TYPE: Byte = 3
        const val INT_TYPE: Byte = 4
        const val SHORT_TYPE: Byte = 5
        const val LONG_TYPE: Byte = 6
        const val FLOAT_TYPE: Byte = 7
        const val DOUBLE_TYPE: Byte = 8
        const val LIST_TYPE: Byte = 9
        const val SET_TYPE: Byte = 10
        const val MAP_TYPE: Byte = 11
        const val FILE_TYPE: Byte = 12
        const val CLASS_TYPE: Byte = 13
        const val BEAN: Byte = 14

        // Logging type
        const val LOGGER_TYPE: Byte = 40

        // Gradle types
        const val THIS_TASK: Byte = 80
        const val FILE_TREE_TYPE: Byte = 81
        const val FILE_COLLECTION_TYPE: Byte = 82
        const val ARTIFACT_COLLECTION_TYPE: Byte = 83
        const val OBJECT_FACTORY_TYPE: Byte = 84

        // Internal Gradle types
        const val FILE_RESOLVER_TYPE: Byte = 90
        const val PATTERN_SPEC_FACTORY_TYPE: Byte = 91
        const val FILE_COLLECTION_FACTORY_TYPE: Byte = 92
        const val INSTANTIATOR_TYPE: Byte = 93
        const val FILE_OPERATIONS_TYPE: Byte = 94
        const val DEFAULT_COPY_SPEC: Byte = 95
        const val DESTINATION_ROOT_COPY_SPEC: Byte = 96
    }
}


private
fun <T> Encoder.writeWithTag(tag: Byte, serializer: Serializer<T>, value: T) {
    writeByte(tag)
    serializer.write(this, value)
}


private
fun collectSourcePathsFrom(copySpec: DefaultCopySpec, files: MutableList<File>) {
    files.addAll(copySpec.resolveSourceFiles())
    for (child in copySpec.children) {
        collectSourcePathsFrom(child as DefaultCopySpec, files)
    }
}
