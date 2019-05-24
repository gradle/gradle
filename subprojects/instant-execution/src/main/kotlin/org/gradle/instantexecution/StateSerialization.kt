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
import org.gradle.instantexecution.Tags.ARTIFACT_COLLECTION_TYPE
import org.gradle.instantexecution.Tags.BEAN
import org.gradle.instantexecution.Tags.BOOLEAN_TYPE
import org.gradle.instantexecution.Tags.BYTE_TYPE
import org.gradle.instantexecution.Tags.CLASS_TYPE
import org.gradle.instantexecution.Tags.DEFAULT_COPY_SPEC
import org.gradle.instantexecution.Tags.DESTINATION_ROOT_COPY_SPEC
import org.gradle.instantexecution.Tags.DOUBLE_TYPE
import org.gradle.instantexecution.Tags.FILE_COLLECTION_FACTORY_TYPE
import org.gradle.instantexecution.Tags.FILE_COLLECTION_TYPE
import org.gradle.instantexecution.Tags.FILE_OPERATIONS_TYPE
import org.gradle.instantexecution.Tags.FILE_RESOLVER_TYPE
import org.gradle.instantexecution.Tags.FILE_TREE_TYPE
import org.gradle.instantexecution.Tags.FILE_TYPE
import org.gradle.instantexecution.Tags.FLOAT_TYPE
import org.gradle.instantexecution.Tags.INSTANTIATOR_TYPE
import org.gradle.instantexecution.Tags.INT_TYPE
import org.gradle.instantexecution.Tags.LIST_TYPE
import org.gradle.instantexecution.Tags.LOGGER_TYPE
import org.gradle.instantexecution.Tags.LONG_TYPE
import org.gradle.instantexecution.Tags.MAP_TYPE
import org.gradle.instantexecution.Tags.NULL_VALUE
import org.gradle.instantexecution.Tags.OBJECT_FACTORY_TYPE
import org.gradle.instantexecution.Tags.PATTERN_SPEC_FACTORY_TYPE
import org.gradle.instantexecution.Tags.SET_TYPE
import org.gradle.instantexecution.Tags.SHORT_TYPE
import org.gradle.instantexecution.Tags.STRING_TYPE
import org.gradle.instantexecution.Tags.THIS_TASK
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
    directoryFileTreeFactory: DirectoryFileTreeFactory,
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
    val fileTreeSerializer = FileTreeSerializer(fileSetSerializer, directoryFileTreeFactory)

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
                writeClass(value)
            }
            is List<*> -> collectionSerializerFor(LIST_TYPE)
            is Set<*> -> collectionSerializerFor(SET_TYPE)
            // Only serialize certain Map implementations for now, as some custom types extend Map (eg DefaultManifest)
            is HashMap<*, *> -> mapSerializer()
            is LinkedHashMap<*, *> -> mapSerializer()
            is Logger -> writer { value ->
                require(value is Logger)
                writeByte(LOGGER_TYPE)
                writeLogger(value)
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
            is DefaultCopySpec -> writer { value ->
                require(value is DefaultCopySpec)
                writeByte(DEFAULT_COPY_SPEC)
                writeDefaultCopySpec(value)
            }
            is DestinationRootCopySpec -> writer { value ->
                require(value is DestinationRootCopySpec)
                writeByte(DESTINATION_ROOT_COPY_SPEC)
                writeDestinationRootCopySpec(value)
            }
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
            else -> writer { value ->
                require(value != null)
                writeByte(BEAN)
                writeBean(value)
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
            CLASS_TYPE -> readClass()
            LIST_TYPE -> readCollectionInto { size -> ArrayList<Any?>(size) }
            SET_TYPE -> readCollectionInto { size -> LinkedHashSet<Any?>(size) }
            MAP_TYPE -> readMap()
            LOGGER_TYPE -> readLogger()
            THIS_TASK -> isolate.owner
            FILE_TREE_TYPE -> fileTreeSerializer.read(this)
            FILE_COLLECTION_TYPE -> fileCollectionFactory.fixed(fileSetSerializer.read(this))
            ARTIFACT_COLLECTION_TYPE -> EmptyArtifactCollection(ImmutableFileCollection.of())
            OBJECT_FACTORY_TYPE -> objectFactory
            FILE_RESOLVER_TYPE -> fileResolver
            INSTANTIATOR_TYPE -> instantiator
            PATTERN_SPEC_FACTORY_TYPE -> patternSpecFactory
            FILE_COLLECTION_FACTORY_TYPE -> fileCollectionFactory
            FILE_OPERATIONS_TYPE -> readProjectService<FileOperations>()
            DEFAULT_COPY_SPEC -> readDefaultCopySpec(fileResolver, instantiator)
            DESTINATION_ROOT_COPY_SPEC -> readDestinationRootCopySpec(fileResolver)
            BEAN -> readBean(filePropertyFactory)
            else -> throw UnsupportedOperationException()
        }
    }
}


private
fun <T> Encoder.writeWithTag(tag: Byte, serializer: Serializer<T>, value: T) {
    writeByte(tag)
    serializer.write(this, value)
}


private
inline fun <reified T> ReadContext.readProjectService() =
    ownerProject
        .services
        .get(T::class.java)


internal
val IsolateContext.ownerProject
    get() = (isolate.owner.project as ProjectInternal)


internal
fun WriteContext.writeClass(value: Class<*>) {
    writeString(value.name)
}


internal
fun ReadContext.readClass(): Class<*> =
    taskClassLoader.loadClass(readString())


private
fun WriteContext.projectStateType(type: KClass<*>): ValueSerializer? {
    logger.warn("instant-execution > Cannot serialize object of type ${type.java.name} as these are not supported with instant execution.")
    return null
}


internal
fun WriteContext.writeLogger(value: Logger) {
    writeString(value.name)
}


internal
fun ReadContext.readLogger() =
    LoggerFactory.getLogger(readString())


private
fun WriteContext.writeDefaultCopySpec(value: DefaultCopySpec) {
    val allSourcePaths = ArrayList<File>()
    collectSourcePathsFrom(value, allSourcePaths)
    write(allSourcePaths)
}


private
fun ReadContext.readDefaultCopySpec(fileResolver: FileResolver, instantiator: Instantiator): DefaultCopySpec {
    @Suppress("unchecked_cast")
    val sourceFiles = read() as List<File>
    val copySpec = DefaultCopySpec(fileResolver, instantiator)
    copySpec.from(sourceFiles)
    return copySpec
}


private
fun WriteContext.writeDestinationRootCopySpec(value: DestinationRootCopySpec) {
    write(value.destinationDir)
    write(value.delegate)
}


private
fun ReadContext.readDestinationRootCopySpec(fileResolver: FileResolver): DestinationRootCopySpec {
    val destDir = read() as? File
    val delegate = read() as CopySpecInternal
    val spec = DestinationRootCopySpec(fileResolver, delegate)
    destDir?.let(spec::into)
    return spec
}


private
fun WriteContext.writeBean(value: Any) {
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
fun ReadContext.readBean(filePropertyFactory: FilePropertyFactory): Any {
    val id = readSmallInt()
    val previousValue = isolate.identities.getInstance(id)
    if (previousValue != null) {
        return previousValue
    }
    val beanType = readClass()
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


// TODO: What about the runtime decorations a serialized bean might have had at configuration time?
private
fun newConstructorForSerialization(beanType: Class<*>, constructor: Constructor<*>): Constructor<out Any> =
    ReflectionFactory.getReflectionFactory().newConstructorForSerialization(beanType, constructor)


private
fun collectionSerializerFor(tag: Byte): ValueSerializer = writer { value ->
    require(value is Collection<*>)
    writeByte(tag)
    writeCollection(value)
}


private
fun mapSerializer(): ValueSerializer = writer { value ->
    require(value is Map<*, *>)
    writeByte(MAP_TYPE)
    writeMap(value)
}


internal
fun WriteContext.writeCollection(value: Collection<*>) {
    writeCollection(value) { write(it) }
}


internal
fun <T : MutableCollection<Any?>> ReadContext.readCollectionInto(factory: (Int) -> T): T =
    readCollectionInto(factory) { read() }


internal
fun WriteContext.writeMap(value: Map<*, *>) {
    writeSmallInt(value.size)
    for (entry in value.entries) {
        write(entry.key)
        write(entry.value)
    }
}


internal
fun ReadContext.readMap(): Map<Any?, Any?> {
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
class FileTreeSerializer(
    private val fileSetSerializer: SetSerializer<File>,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory
) : Serializer<FileTreeInternal> {

    override fun write(encoder: Encoder, value: FileTreeInternal) {
        fileSetSerializer.write(encoder, fileTreeRootsOf(value))
    }

    override fun read(decoder: Decoder): FileTreeInternal =
        DefaultCompositeFileTree(
            fileSetSerializer.read(decoder).map {
                FileTreeAdapter(directoryFileTreeFactory.create(it))
            }
        )

    private
    fun fileTreeRootsOf(value: FileTreeInternal): LinkedHashSet<File> {
        val visitor = FileTreeVisitor()
        value.visitLeafCollections(visitor)
        return visitor.roots
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
}


private
fun collectSourcePathsFrom(copySpec: DefaultCopySpec, files: MutableList<File>) {
    files.addAll(copySpec.resolveSourceFiles())
    for (child in copySpec.children) {
        collectSourcePathsFrom(child as DefaultCopySpec, files)
    }
}
