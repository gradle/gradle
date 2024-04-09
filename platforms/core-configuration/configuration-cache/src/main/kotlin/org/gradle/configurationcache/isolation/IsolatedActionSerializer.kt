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

package org.gradle.configurationcache.isolation

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.gradle.api.IsolatedAction
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.extensions.useToRun
import org.gradle.configurationcache.logger
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.serialization.ClassDecoder
import org.gradle.configurationcache.serialization.ClassEncoder
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.beans.BeanStateReaderLookup
import org.gradle.configurationcache.serialization.beans.BeanStateWriterLookup
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.configurationcache.serialization.withIsolate
import org.gradle.configurationcache.services.IsolatedActionCodecsFactory
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.IdentityHashMap


/**
 * Serialized state of an object graph containing one or more [IsolatedAction]s.
 */
class SerializedAction(
    /**
     * The serialized graph.
     */
    val graph: ByteArray,

    /**
     * External references that are not serialized directly as part of the [graph].
     * These include [Class] and [BuildServiceProvider] references.
     */
    val environment: Map<Int, Any>,
)


internal
class IsolatedActionSerializer(
    private val owner: IsolateOwner,
    private val beanStateWriterLookup: BeanStateWriterLookup,
    private val isolatedActionCodecs: IsolatedActionCodecsFactory
) {
    fun serialize(action: Any): SerializedAction {
        val outputStream = ByteArrayOutputStream()
        val classCollector = ClassCollector()
        serializeTo(outputStream, classCollector, action)
        return SerializedAction(
            outputStream.toByteArray(),
            classCollector.toLookup()
        )
    }

    private
    fun serializeTo(outputStream: ByteArrayOutputStream, classCollector: ClassCollector, action: Any) {
        writeContextFor(outputStream, classCollector).useToRun {
            runWriteOperation {
                withIsolate(owner) {
                    write(action)
                }
            }
        }
    }

    private
    fun writeContextFor(
        outputStream: OutputStream,
        classEncoder: ClassEncoder,
    ) = DefaultWriteContext(
        codec = isolatedActionCodecs.isolatedActionCodecs(),
        encoder = KryoBackedEncoder(outputStream),
        beanStateWriterLookup = beanStateWriterLookup,
        logger = logger,
        tracer = null,
        problemsListener = ThrowingProblemsListener,
        classEncoder = classEncoder
    )
}


internal
class IsolatedActionDeserializer(
    private val owner: IsolateOwner,
    private val beanStateReaderLookup: BeanStateReaderLookup,
    private val isolatedActionCodecs: IsolatedActionCodecsFactory
) {
    fun deserialize(action: SerializedAction): Any =
        readerContextFor(action).useToRun {
            runReadOperation {
                withIsolate(owner) {
                    readNonNull()
                }
            }
        }

    private
    fun readerContextFor(action: SerializedAction) = DefaultReadContext(
        codec = isolatedActionCodecs.isolatedActionCodecs(),
        decoder = KryoBackedDecoder(action.graph.inputStream()),
        beanStateReaderLookup = beanStateReaderLookup,
        logger = logger,
        problemsListener = ThrowingProblemsListener,
        classDecoder = object : ClassDecoder {
            override fun ReadContext.decodeClass(): Class<*> =
                action.environment[readSmallInt()]?.uncheckedCast()!!
        }
    )
}


private
class ClassCollector : ClassEncoder {

    private
    val classes = IdentityHashMap<Class<*>, Int>()

    override fun WriteContext.encodeClass(type: Class<*>) {
        val existing = classes[type]
        if (existing != null) {
            writeSmallInt(existing)
        } else {
            val id = classes.size
            classes[type] = id
            writeSmallInt(id)
        }
    }

    fun toLookup(): Map<Int, Any> = classes.run {
        Int2ObjectOpenHashMap<Any>(size).also { result ->
            forEach { (obj, id) ->
                result.put(id, obj)
            }
        }
    }
}


private
object ThrowingProblemsListener : ProblemsListener {
    override fun onProblem(problem: PropertyProblem) {
        TODO("Not yet implemented: $problem")
    }
}


