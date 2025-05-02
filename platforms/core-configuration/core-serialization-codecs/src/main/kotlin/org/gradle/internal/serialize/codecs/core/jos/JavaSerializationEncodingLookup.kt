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

package org.gradle.internal.serialize.codecs.core.jos

import org.gradle.internal.serialize.graph.codecs.Encoding
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.ObjectOutputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap


/**
 * This service determines the [Encoding] to be used for a type, if covered by the
 * Java object serialization support in the configuration cache.
 */
@ServiceScope(Scope.BuildTree::class)
class JavaSerializationEncodingLookup {
    private
    val encodings = ConcurrentHashMap<Class<*>, EncodingDetails>()

    /**
     * Returns the proper encoding provider for the given type, or null, if not covered by Java Object serialization.
     */
    fun encodingFor(type: Class<*>): Encoding? {
        return encodings.computeIfAbsent(type) { t -> calculateEncoding(t) }.encoding
    }

    private
    fun calculateEncoding(type: Class<*>): EncodingDetails {
        val candidates = type.allMethods()
        val encoding = writeReplaceEncodingFor(candidates)
            ?: readResolveEncodingFor(candidates)
            ?: writeObjectEncodingFor(candidates)
            ?: readObjectEncodingFor(candidates)
        return EncodingDetails(encoding)
    }

    private
    fun writeReplaceEncodingFor(candidates: List<Method>) =
        writeReplaceMethodFrom(candidates)
            ?.let(JavaObjectSerializationCodec::WriteReplaceEncoding)

    private
    fun readResolveEncodingFor(candidates: List<Method>) =
        readResolveMethodFrom(candidates)
            ?.let { JavaObjectSerializationCodec.ReadResolveEncoding }

    private
    fun writeObjectEncodingFor(candidates: List<Method>): Encoding? =
        writeObjectMethodHierarchyFrom(candidates)
            .takeIf { it.isNotEmpty() }
            ?.let(JavaObjectSerializationCodec::WriteObjectEncoding)

    private
    fun readObjectEncodingFor(candidates: List<Method>): Encoding? =
        readObjectMethodHierarchyFrom(candidates)
            .takeIf { it.isNotEmpty() }
            ?.let { JavaObjectSerializationCodec.ReadObjectEncoding }

    private
    fun writeReplaceMethodFrom(candidates: List<Method>) =
        candidates.firstAccessibleMatchingMethodOrNull {
            !Modifier.isStatic(modifiers)
                && parameterCount == 0
                && returnType == java.lang.Object::class.java
                && name == "writeReplace"
        }

    private
    fun writeObjectMethodHierarchyFrom(candidates: List<Method>) = candidates
        .serializationMethodHierarchy("writeObject", ObjectOutputStream::class.java)

    private
    fun readResolveMethodFrom(candidates: List<Method>) =
        candidates.find {
            it.isReadResolve()
        }

    private
    class EncodingDetails(
        val encoding: Encoding?
    )
}
