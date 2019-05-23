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
import java.lang.reflect.Constructor
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

    fun newDeserializer(): StateDeserializer =
        DefaultStateDeserializer()

    private
    inner class DefaultStateSerializer : StateSerializer {

        override fun WriteContext.serializerFor(candidate: Any?): ValueSerializer? = when (candidate) {
            null -> writer {
                writeByte(NULL_VALUE)
            }
            is String -> writer { value ->
                require(value is String)
                writeWithTag(STRING_TYPE, stringSerializer, value)
            }
            is Boolean -> writer { value ->
                require(value is Boolean)
                writeWithTag(BOOLEAN_TYPE, booleanSerializer, value)
            }
            is Int -> writer { value ->
                require(value is Int)
                writeWithTag(INT_TYPE, integerSerializer, value)
            }
            is Short -> writer { value ->
                require(value is Short)
                writeWithTag(SHORT_TYPE, shortSerializer, value)
            }
            is Long -> writer { value ->
                require(value is Long)
                writeWithTag(LONG_TYPE, longSerializer, value)
            }
            is Byte -> writer { value ->
                require(value is Byte)
                writeWithTag(BYTE_TYPE, byteSerializer, value)
            }
            is Float -> writer { value ->
                require(value is Float)
                writeWithTag(FLOAT_TYPE, floatSerializer, value)
            }
            is Double -> writer { value ->
                require(value is Double)
                writeWithTag(DOUBLE_TYPE, doubleSerializer, value)
            }
            is FileTreeInternal -> writer { value ->
                require(value is FileTreeInternal)
                writeWithTag(FILE_TREE_TYPE, fileTreeSerializer, value)
            }
            is File -> writer { value ->
                require(value is File)
                writeWithTag(FILE_TYPE, BaseSerializerFactory.FILE_SERIALIZER, value)
            }
            is Class<*> -> writer { value ->
                require(value is Class<*>)
                writeByte(CLASS_TYPE)
                writeString(value.name)
            }
            is List<*> -> collectionSerializerFor(LIST_TYPE)
            is Set<*> -> collectionSerializerFor(SET_TYPE)
            // Only serialize certain Map implementations for now, as some custom types extend Map (eg DefaultManifest)
            is HashMap<*, *> -> mapSerializerFor()
            is LinkedHashMap<*, *> -> mapSerializerFor()
            is Logger -> writer { value ->
                require(value is Logger)
                writeByte(LOGGER_TYPE)
                writeString(value.name)
            }
            is FileCollection -> writer { value ->
                require(value is FileCollection)
                writeWithTag(FILE_COLLECTION_TYPE, fileSetSerializer, value.files)
            }
            is ArtifactCollection -> writer { value ->
                require(value is ArtifactCollection)
                writeByte(ARTIFACT_COLLECTION_TYPE)
            }
            is ObjectFactory -> writer { value ->
                require(value is ObjectFactory)
                writeByte(OBJECT_FACTORY_TYPE)
            }
            is PatternSpecFactory -> writer { value ->
                require(value is PatternSpecFactory)
                writeByte(PATTERN_SPEC_FACTORY_TYPE)
            }
            is FileResolver -> writer { value ->
                require(value is FileResolver)
                writeByte(FILE_RESOLVER_TYPE)
            }
            is Instantiator -> writer { value ->
                require(value is Instantiator)
                writeByte(INSTANTIATOR_TYPE)
            }
            is FileCollectionFactory -> writer { value ->
                require(value is FileCollectionFactory)
                writeByte(FILE_COLLECTION_FACTORY_TYPE)
            }
            is DefaultCopySpec -> defaultCopySpecSerializerFor()
            is DestinationRootCopySpec -> destinationRootCopySpecSerializerFor()
            is Project -> projectStateType(Project::class)
            is Gradle -> projectStateType(Gradle::class)
            is Settings -> projectStateType(Settings::class)
            is Task -> writer { value ->
                if (value === isolate.owner) {
                    writeByte(THIS_TASK)
                } else {
                    projectStateType(Task::class)
                    writeByte(NULL_VALUE)
                }
            }
            is FileOperations -> writer { value ->
                require(value is FileOperations)
                writeByte(FILE_OPERATIONS_TYPE)
            }
            else -> beanSerializerFor()
        }

        private
        fun WriteContext.projectStateType(type: KClass<*>): ValueSerializer? {
            LoggerFactory.getLogger(StateSerialization::class.java).warn("instant-execution > Cannot serialize object of type ${type.java.name} as these are not supported with instant execution.")
            return null
        }

        private
        fun WriteContext.defaultCopySpecSerializerFor(): ValueSerializer = writer { value ->
            require(value is DefaultCopySpec)
            val allSourcePaths = ArrayList<File>()
            collectSourcePathsFrom(value, allSourcePaths)
            writeByte(DEFAULT_COPY_SPEC)
            write(allSourcePaths)
        }

        private
        fun WriteContext.destinationRootCopySpecSerializerFor(): ValueSerializer = writer { value ->
            require(value is DestinationRootCopySpec)
            writeByte(DESTINATION_ROOT_COPY_SPEC)
            write(value.destinationDir)
            write(value.delegate)
        }

        private
        fun WriteContext.beanSerializerFor(): ValueSerializer = writer { value ->
            require(value != null)
            writeByte(BEAN)
            val id = isolate.identities.getId(value)
            if (id != null) {
                writeSmallInt(id)
            } else {
                writeSmallInt(isolate.identities.putInstance(value))
                val beanType = GeneratedSubclasses.unpackType(value)
                writeString(beanType.name)
                BeanFieldSerializer(beanType).run {
                    serialize(value)
                }
            }
        }

        private
        fun WriteContext.collectionSerializerFor(tag: Byte): ValueSerializer = writer { value ->
            require(value is Collection<*>)
            writeByte(tag)
            writeSmallInt(value.size)
            for (item in value) {
                write(item)
            }
        }

        private
        fun WriteContext.mapSerializerFor(): ValueSerializer = writer { value ->
            require(value is Map<*, *>)
            writeByte(MAP_TYPE)
            writeSmallInt(value.size)
            for (entry in value.entries) {
                write(entry.key)
                write(entry.value)
            }
        }
    }

    private
    inner class DefaultStateDeserializer : StateDeserializer {

        override fun ReadContext.deserialize(): Any? = when (readByte()) {
            NULL_VALUE -> null
            STRING_TYPE -> stringSerializer.read(this)
            BOOLEAN_TYPE -> booleanSerializer.read(this)
            INT_TYPE -> integerSerializer.read(this)
            SHORT_TYPE -> shortSerializer.read(this)
            LONG_TYPE -> longSerializer.read(this)
            BYTE_TYPE -> byteSerializer.read(this)
            DOUBLE_TYPE -> doubleSerializer.read(this)
            FLOAT_TYPE -> floatSerializer.read(this)
            FILE_TYPE -> BaseSerializerFactory.FILE_SERIALIZER.read(this)
            CLASS_TYPE -> taskClassLoader.loadClass(readString())
            LIST_TYPE -> deserializeCollection { ArrayList<Any?>(it) }
            SET_TYPE -> deserializeCollection { LinkedHashSet<Any?>(it) }
            MAP_TYPE -> deserializeMap()
            LOGGER_TYPE -> {
                LoggerFactory.getLogger(readString())
            }
            THIS_TASK -> isolate.owner
            FILE_TREE_TYPE -> fileTreeSerializer.read(this)
            FILE_COLLECTION_TYPE -> fileCollectionFactory.fixed(fileSetSerializer.read(this))
            ARTIFACT_COLLECTION_TYPE -> EmptyArtifactCollection(ImmutableFileCollection.of())
            OBJECT_FACTORY_TYPE -> objectFactory
            FILE_RESOLVER_TYPE -> fileResolver
            INSTANTIATOR_TYPE -> instantiator
            PATTERN_SPEC_FACTORY_TYPE -> patternSpecFactory
            FILE_COLLECTION_FACTORY_TYPE -> fileCollectionFactory
            FILE_OPERATIONS_TYPE -> (isolate.owner.project as ProjectInternal).services.get(FileOperations::class.java)
            DEFAULT_COPY_SPEC -> deserializeDefaultCopySpec()
            DESTINATION_ROOT_COPY_SPEC -> deserializeDestinationRootCopySpec()
            BEAN -> deserializeBean()
            else -> throw UnsupportedOperationException()
        }

        private
        fun ReadContext.deserializeBean(): Any {
            val id = readSmallInt()
            val previousValue = isolate.identities.getInstance(id)
            if (previousValue != null) {
                return previousValue
            }
            val beanTypeName = readString()
            val beanType = taskClassLoader.loadClass(beanTypeName)
            val constructor = if (GroovyObjectSupport::class.java.isAssignableFrom(beanType)) {
                // Run the `GroovyObjectSupport` constructor, to initialize the metadata field
                newConstructorForSerialization(beanType, GroovyObjectSupport::class.java.getConstructor())
            } else {
                newConstructorForSerialization(beanType, Object::class.java.getConstructor())
            }
            val bean = constructor.newInstance()
            isolate.identities.putInstance(id, bean)
            BeanFieldDeserializer(bean.javaClass, filePropertyFactory).run {
                deserialize(bean)
            }
            return bean
        }

        private
        fun ReadContext.deserializeDestinationRootCopySpec(): DestinationRootCopySpec {
            val destDir = read() as? File
            val delegate = read() as CopySpecInternal
            val spec = DestinationRootCopySpec(fileResolver, delegate)
            destDir?.let(spec::into)
            return spec
        }

        private
        fun ReadContext.deserializeDefaultCopySpec(): DefaultCopySpec {
            @Suppress("unchecked_cast")
            val sourceFiles = read() as List<File>
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
            fileSetSerializer.write(encoder, fileTreeRootsOf(value))
        }

        private
        fun fileTreeRootsOf(value: FileTreeInternal): LinkedHashSet<File> {
            val visitor = FileTreeVisitor()
            value.visitLeafCollections(visitor)
            return visitor.roots
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
fun newConstructorForSerialization(beanType: Class<*>, constructor: Constructor<*>): Constructor<out Any> =
    ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, constructor)


private
fun <T : MutableCollection<Any?>> ReadContext.deserializeCollection(factory: (Int) -> T): T {
    val size = readSmallInt()
    val items = factory(size)
    for (i in 0 until size) {
        items.add(read())
    }
    return items
}


private
fun ReadContext.deserializeMap(): Map<Any?, Any?> {
    val size = readSmallInt()
    val items = LinkedHashMap<Any?, Any?>()
    for (i in 0 until size) {
        val key = read()
        val value = read()
        items[key] = value
    }
    return items
}


private
fun collectSourcePathsFrom(copySpec: DefaultCopySpec, files: MutableList<File>) {
    files.addAll(copySpec.resolveSourceFiles())
    for (child in copySpec.children) {
        collectSourcePathsFrom(child as DefaultCopySpec, files)
    }
}
