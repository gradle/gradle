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

import org.gradle.api.IsolatedAction
import org.gradle.configurationcache.ConfigurationCacheError
import org.gradle.internal.extensions.stdlib.invert
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.configurationcache.logger
import org.gradle.configurationcache.problems.AbstractProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.serialize.graph.ClassDecoder
import org.gradle.internal.serialize.graph.ClassEncoder
import org.gradle.internal.serialize.graph.DefaultReadContext
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.configurationcache.serialization.beans.DefaultBeanStateReaderLookup
import org.gradle.configurationcache.serialization.beans.DefaultBeanStateWriterLookup
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.configurationcache.services.IsolatedActionCodecsFactory
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.IdentityHashMap


/**
 * Serialized state of an object graph containing one or more [IsolatedAction]s.
 *
 * @param G type of the root object stored in [graph]
 */
internal
class SerializedIsolatedActionGraph<G>(
    /**
     * The serialized graph.
     */
    val graph: ByteArray,

    /**
     * External references that are not serialized directly as part of the [graph].
     * These might include references to classes, value sources and build services.
     *
     * Maps the integer written to the serialized [graph] to the external reference.
     * See [EnvironmentEncoder] and [EnvironmentDecoder] for details.
     */
    val environment: Map<Int, Any>,
)


internal
class IsolatedActionSerializer(
    private val owner: IsolateOwner,
    private val beanStateWriterLookup: DefaultBeanStateWriterLookup,
    private val isolatedActionCodecs: IsolatedActionCodecsFactory
) {
    fun <G : Any> serialize(action: G): SerializedIsolatedActionGraph<G> {
        val outputStream = ByteArrayOutputStream()
        val environmentEncoder = EnvironmentEncoder()
        serializeTo(outputStream, environmentEncoder, action)
        return SerializedIsolatedActionGraph(
            outputStream.toByteArray(),
            environmentEncoder.getResultingEnvironment()
        )
    }

    private
    fun serializeTo(
        outputStream: ByteArrayOutputStream,
        environmentEncoder: EnvironmentEncoder,
        action: Any
    ) {
        writeContextFor(outputStream, environmentEncoder).useToRun {
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
    private val beanStateReaderLookup: DefaultBeanStateReaderLookup,
    private val isolatedActionCodecs: IsolatedActionCodecsFactory
) {
    fun <G : Any> deserialize(action: SerializedIsolatedActionGraph<G>): G =
        readerContextFor(action).useToRun {
            runReadOperation {
                withIsolate(owner) {
                    readNonNull()
                }
            }
        }

    private
    fun readerContextFor(
        action: SerializedIsolatedActionGraph<*>
    ) = DefaultReadContext(
        codec = isolatedActionCodecs.isolatedActionCodecs(),
        decoder = KryoBackedDecoder(action.graph.inputStream()),
        beanStateReaderLookup = beanStateReaderLookup,
        logger = logger,
        problemsListener = ThrowingProblemsListener,
        classDecoder = EnvironmentDecoder(action.environment)
    )
}


private
class EnvironmentEncoder : ClassEncoder {

    private
    val refs = IdentityHashMap<Class<*>, Int>()

    override fun WriteContext.encodeClass(type: Class<*>) {
        writeSmallInt(refs.computeIfAbsent(type) { refs.size })
    }

    fun getResultingEnvironment(): Map<Int, Any> =
        refs.invert()
}


private
class EnvironmentDecoder(
    val environment: Map<Int, Any>
) : ClassDecoder {
    override fun ReadContext.decodeClass(): Class<*> =
        environment[readSmallInt()]?.uncheckedCast()!!
}


/**
 * TODO: report problems via the Problems API
 */
private
object ThrowingProblemsListener : AbstractProblemsListener() {
    override fun onProblem(problem: PropertyProblem) {
        // TODO: consider throwing more specific exception
        throw ConfigurationCacheError("Failed to isolate 'GradleLifecycle' action: ${problem.message}")
    }
}
