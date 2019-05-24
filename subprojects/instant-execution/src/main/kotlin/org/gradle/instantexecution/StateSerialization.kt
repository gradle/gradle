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
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.api.tasks.util.internal.PatternSpecFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory.BOOLEAN_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BYTE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.DOUBLE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FLOAT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.LONG_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.SHORT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER
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
    val fileSetSerializer = SetSerializer(FILE_SERIALIZER)

    private
    val fileTreeSerializer = FileTreeSerializer(fileSetSerializer, directoryFileTreeFactory)

    private
    val bindings = bindings {

        bind(STRING_SERIALIZER)
        bind(BOOLEAN_SERIALIZER)
        bind(INTEGER_SERIALIZER)
        bind(SHORT_SERIALIZER)
        bind(LONG_SERIALIZER)
        bind(BYTE_SERIALIZER)
        bind(FLOAT_SERIALIZER)
        bind(DOUBLE_SERIALIZER)
        bind(fileTreeSerializer)
        bind(FILE_SERIALIZER)
        bind(classCodec)

        bind(listCodec)
        bind(setCodec)

        // Only serialize certain Map implementations for now, as some custom types extend Map (eg DefaultManifest)
        bind(HashMap::class, mapCodec)

        bind(loggerCodec)

        bind(FileCollectionCodec(fileCollectionFactory, fileSetSerializer))
        bind(artifactCollectionCodec)

        bind(singleton(objectFactory))
        bind(singleton(patternSpecFactory))
        bind(singleton(fileResolver))
        bind(singleton(instantiator))
        bind(singleton(fileCollectionFactory))

        bind(DefaultCopySpecCodec(fileResolver, instantiator))
        bind(DestinationRootCopySpecCodec(fileResolver))

        bind(OwnerTaskCodec)

        bind(ownerProjectService<FileOperations>())

        bind(BeanCodec(filePropertyFactory))
    }

    fun newSerializer(): StateSerializer =
        DefaultStateSerializer()

    fun newDeserializer(): StateDeserializer =
        DefaultStateDeserializer()

    private
    inner class DefaultStateSerializer : StateSerializer {

        private
        val nullWriter = writer {
            writeByte(NULL_VALUE)
        }

        override fun WriteContext.serializerFor(candidate: Any?): ValueSerializer? = when (candidate) {
            null -> nullWriter
            is Project -> projectStateType(Project::class)
            is Gradle -> projectStateType(Gradle::class)
            is Settings -> projectStateType(Settings::class)
            else -> candidate.javaClass.let { type ->
                bindings.find { it.type.isAssignableFrom(type) }?.run {
                    writer { value ->
                        writeByte(tag)
                        codec.run {
                            encode(value!!)
                        }
                    }
                }
            }
        }
    }

    private
    inner class DefaultStateDeserializer : StateDeserializer {

        override fun ReadContext.deserialize(): Any? = when (val tag = readByte()) {
            NULL_VALUE -> null
            else -> bindings[tag.toInt()].codec.run { decode() }
        }
    }

    internal
    companion object {
        const val NULL_VALUE: Byte = -1
    }
}


internal
object OwnerTaskCodec : Codec<Task> {

    override fun WriteContext.encode(value: Task) {
        if (value === isolate.owner) {
            writeBoolean(true)
        } else {
            logUnsupported(Task::class)
            writeBoolean(false)
        }
    }

    override fun ReadContext.decode(): Task? =
        isolate.owner.takeIf { readBoolean() }
}


internal
class FileCollectionCodec(
    private val fileCollectionFactory: FileCollectionFactory,
    private val fileSetSerializer: SetSerializer<File>
) : Codec<FileCollection> {

    override fun WriteContext.encode(value: FileCollection) =
        fileSetSerializer.write(this, value.files)

    override fun ReadContext.decode(): FileCollection? =
        fileCollectionFactory.fixed(fileSetSerializer.read(this))
}


internal
inline fun <reified T> ownerProjectService() =
    codec<T>({ }, { readProjectService() })


private
inline fun <reified T> ReadContext.readProjectService() =
    ownerProject
        .services
        .get(T::class.java)


internal
val IsolateContext.ownerProject
    get() = isolate.owner.project as ProjectInternal


internal
fun WriteContext.writeClass(value: Class<*>) {
    writeString(value.name)
}


internal
fun ReadContext.readClass(): Class<*> =
    taskClassLoader.loadClass(readString())


private
fun IsolateContext.projectStateType(type: KClass<*>): ValueSerializer? {
    logUnsupported(type)
    return null
}


internal
fun IsolateContext.logUnsupported(type: KClass<*>) {
    logger.warn("instant-execution > Cannot serialize object of type ${type.java.name} as these are not supported with instant execution.")
}


internal
fun WriteContext.writeLogger(value: Logger) {
    writeString(value.name)
}


internal
fun ReadContext.readLogger() =
    LoggerFactory.getLogger(readString())


internal
class DefaultCopySpecCodec(
    private val fileResolver: FileResolver,
    private val instantiator: Instantiator
) : Codec<DefaultCopySpec> {

    override fun WriteContext.encode(value: DefaultCopySpec) =
        writeDefaultCopySpec(value)

    override fun ReadContext.decode(): DefaultCopySpec =
        readDefaultCopySpec(fileResolver, instantiator)
}


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


internal
class DestinationRootCopySpecCodec(
    private val fileResolver: FileResolver
) : Codec<DestinationRootCopySpec> {

    override fun WriteContext.encode(value: DestinationRootCopySpec) =
        writeDestinationRootCopySpec(value)

    override fun ReadContext.decode(): DestinationRootCopySpec =
        readDestinationRootCopySpec(fileResolver)
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


internal
class BeanCodec(
    private val filePropertyFactory: FilePropertyFactory
) : Codec<Any> {

    override fun WriteContext.encode(value: Any) =
        writeBean(value)

    override fun ReadContext.decode(): Any? =
        readBean(filePropertyFactory)
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


internal
fun ReadContext.readList() = readCollectionInto { size -> ArrayList<Any?>(size) }


internal
fun ReadContext.readSet() = readCollectionInto { size -> LinkedHashSet<Any?>(size) }


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
