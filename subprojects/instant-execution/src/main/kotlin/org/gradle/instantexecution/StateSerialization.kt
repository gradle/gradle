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
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.tasks.util.internal.PatternSpecFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SetSerializer
import org.slf4j.LoggerFactory
import sun.reflect.ReflectionFactory
import java.io.File
import kotlin.reflect.KClass


class StateSerialization(
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val fileCollectionFactory: FileCollectionFactory,
    private val fileResolver: FileResolver,
    private val instantiator: Instantiator,
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
    val longSerializer = BaseSerializerFactory.LONG_SERIALIZER

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
            is Long -> { encoder, _ ->
                encoder.writeWithTag(LONG_TYPE, longSerializer, value)
            }
            is FileTreeInternal -> { encoder, _ ->
                encoder.writeWithTag(FILE_TREE_TYPE, fileTreeSerializer, value)
            }
            is File -> { encoder, _ ->
                encoder.writeWithTag(FILE_TYPE, BaseSerializerFactory.FILE_SERIALIZER, value)
            }
            is FileCollection -> { encoder, _ ->
                encoder.writeWithTag(FILE_COLLECTION_TYPE, fileSetSerializer, value.files)
            }
            is List<*> -> collectionSerializerFor(LIST_TYPE, value)
            is Set<*> -> collectionSerializerFor(SET_TYPE, value)
            // Only specific implementations, as some custom types extend Map (eg DefaultManifest)
            is HashMap<*, *> -> mapSerializerFor(value)
            is LinkedHashMap<*, *> -> mapSerializerFor(value)
            is ArtifactCollection -> { encoder, _ ->
                encoder.writeByte(ARTIFACT_COLLECTION_TYPE)
            }
            is PatternSpecFactory -> { encoder, _ ->
                encoder.writeByte(PATTERN_SPEC_FACTORY_TYPE)
            }
            is DefaultCopySpec -> defaultCopySpecSerializerFor(value)
            is DestinationRootCopySpec -> destinationRootCopySpecSerializerFor(value)
            is Project -> projectStateType(Project::class)
            is Gradle -> projectStateType(Gradle::class)
            is Settings -> projectStateType(Settings::class)
            is Task -> projectStateType(Task::class)
            else -> beanSerializerFor(value)
        }

        private
        fun projectStateType(type: KClass<*>): ValueSerializer? {
            LoggerFactory.getLogger(StateSerialization::class.java).warn("instant-execution > Cannot serialize object of type ${type.java.name} as these are not supported with instant execution.")
            return null
        }

        private
        fun defaultCopySpecSerializerFor(value: DefaultCopySpec): ValueSerializer = { encoder, listener ->
            val allSourcePaths = ArrayList<File>()
            collectSourcePathsFrom(value, allSourcePaths)
            encoder.writeByte(DEFAULT_COPY_SPEC)
            write(encoder, listener, allSourcePaths)
        }

        private
        fun destinationRootCopySpecSerializerFor(value: DestinationRootCopySpec): ValueSerializer = { encoder, listener ->
            encoder.writeByte(DESTINATION_ROOT_COPY_SPEC)
            write(encoder, listener, value.destinationDir)
            write(encoder, listener, value.delegate)
        }

        private
        fun beanSerializerFor(value: Any): ValueSerializer = { encoder, listener ->
            encoder.writeByte(BEAN)
            val beanType = GeneratedSubclasses.unpackType(value)
            encoder.writeString(beanType.name)
            BeanFieldSerializer(value, beanType, this).invoke(encoder, listener)
        }

        private
        fun collectionSerializerFor(tag: Byte, value: Collection<*>): ValueSerializer = { encoder, listener ->
            encoder.writeByte(tag)
            encoder.writeSmallInt(value.size)
            for (item in value) {
                write(encoder, listener, item)
            }
        }

        private
        fun mapSerializerFor(value: Map<*, *>): ValueSerializer = { encoder, listener ->
            encoder.writeByte(MAP_TYPE)
            encoder.writeSmallInt(value.size)
            for (entry in value.entries) {
                write(encoder, listener, entry.key)
                write(encoder, listener, entry.value)
            }
        }

        private
        fun write(encoder: Encoder, listener: SerializationListener, value: Any?) {
            serializerFor(value)!!.invoke(encoder, listener)
        }
    }

    private
    inner class DefaultStateDeserializer(
        private val beanClassLoader: ClassLoader
    ) : StateDeserializer {

        override fun read(decoder: Decoder, listener: SerializationListener): Any? = when (decoder.readByte()) {
            NULL_VALUE -> null
            STRING_TYPE -> stringSerializer.read(decoder)
            BOOLEAN_TYPE -> booleanSerializer.read(decoder)
            INT_TYPE -> integerSerializer.read(decoder)
            LONG_TYPE -> longSerializer.read(decoder)
            LIST_TYPE -> deserializeCollection(decoder, listener) { ArrayList<Any?>(it) }
            SET_TYPE -> deserializeCollection(decoder, listener) { LinkedHashSet<Any?>(it) }
            MAP_TYPE -> deserializeMap(decoder, listener)
            FILE_TREE_TYPE -> fileTreeSerializer.read(decoder)
            FILE_TYPE -> BaseSerializerFactory.FILE_SERIALIZER.read(decoder)
            FILE_COLLECTION_TYPE -> fileCollectionFactory.fixed(fileSetSerializer.read(decoder))
            ARTIFACT_COLLECTION_TYPE -> EmptyArtifactCollection(ImmutableFileCollection.of())
            PATTERN_SPEC_FACTORY_TYPE -> patternSpecFactory
            DEFAULT_COPY_SPEC -> deserializeDefaultCopySpec(decoder, listener)
            DESTINATION_ROOT_COPY_SPEC -> deserializeDestinationRootCopySpec(decoder, listener)
            BEAN -> deserializeBean(decoder, listener, beanClassLoader)
            else -> throw UnsupportedOperationException()
        }

        private
        fun deserializeBean(decoder: Decoder, listener: SerializationListener, loader: ClassLoader): Any {
            val beanTypeName = decoder.readString()
            val beanType = loader.loadClass(beanTypeName)
            val constructor = if (GroovyObjectSupport::class.java.isAssignableFrom(beanType)) {
                // Run the `GroovyObjectSupport` constructor, to initialize the metadata field
                ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, GroovyObjectSupport::class.java.getConstructor())
            } else {
                ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, Object::class.java.getConstructor())
            }
            val bean = constructor.newInstance()
            BeanFieldDeserializer(bean, bean.javaClass, this, filePropertyFactory).deserialize(decoder, listener)
            return bean
        }

        private
        fun <T : MutableCollection<Any?>> deserializeCollection(decoder: Decoder, listener: SerializationListener, factory: (Int) -> T): T {
            val size = decoder.readSmallInt()
            val items = factory(size)
            for (i in 1..size) {
                items.add(read(decoder, listener))
            }
            return items
        }

        private
        fun deserializeMap(decoder: Decoder, listener: SerializationListener): Map<Any?, Any?> {
            val size = decoder.readSmallInt()
            val items = LinkedHashMap<Any?, Any?>()
            for (i in 1..size) {
                val key = read(decoder, listener)
                val value = read(decoder, listener)
                items.put(key, value)
            }
            return items
        }

        private
        fun deserializeDestinationRootCopySpec(decoder: Decoder, listener: SerializationListener): DestinationRootCopySpec {
            val destDir = read(decoder, listener) as? File
            val delegate = read(decoder, listener) as CopySpecInternal
            val spec = DestinationRootCopySpec(fileResolver, delegate)
            destDir?.let(spec::into)
            return spec
        }

        private
        fun deserializeDefaultCopySpec(decoder: Decoder, listener: SerializationListener): DefaultCopySpec {
            @Suppress("unchecked_cast")
            val sourceFiles = read(decoder, listener) as List<File>
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
        const val NULL_VALUE: Byte = 0
        const val STRING_TYPE: Byte = 1
        const val BOOLEAN_TYPE: Byte = 2
        const val INT_TYPE: Byte = 3
        const val LONG_TYPE: Byte = 4
        const val LIST_TYPE: Byte = 5
        const val SET_TYPE: Byte = 6
        const val MAP_TYPE: Byte = 7
        const val BEAN: Byte = 8

        // Gradle types
        const val FILE_TREE_TYPE: Byte = 9
        const val FILE_TYPE: Byte = 10
        const val FILE_COLLECTION_TYPE: Byte = 11
        const val ARTIFACT_COLLECTION_TYPE: Byte = 12
        const val PATTERN_SPEC_FACTORY_TYPE: Byte = 13
        const val DEFAULT_COPY_SPEC: Byte = 14
        const val DESTINATION_ROOT_COPY_SPEC: Byte = 15
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
