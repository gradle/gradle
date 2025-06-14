/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl.serialize

import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.internal.Describables
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.ReadIdentities
import org.gradle.internal.serialize.graph.WriteIdentities
import org.gradle.internal.serialize.graph.decodePreservingIdentity


internal
interface ScopeLookup {
    fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>?
    val knownClassLoaders: Set<ClassLoader>
}


internal
data class ClassLoaderScopeSpec(
    val parent: ClassLoaderScopeSpec?,
    val name: String,
    val origin: ClassLoaderScopeOrigin?
) {
    var localClassPath: ClassPath = ClassPath.EMPTY
    var localImplementationHash: HashCode? = null
    var exportClassPath: ClassPath = ClassPath.EMPTY

    override fun toString(): String {
        return if (parent != null) {
            "$parent:$name"
        } else {
            name
        }
    }
}


@JvmInline
internal
value class ClassLoaderRole(val local: Boolean)


internal
interface ClassLoaderScopeSpecEncoder {
    fun Encoder.encodeScope(scope: ClassLoaderScopeSpec)
}


internal
interface ClassLoaderScopeSpecDecoder {
    fun Decoder.decodeScope(): ClassLoaderScopeSpec
}


internal
open class InlineClassLoaderScopeSpecEncoder : ClassLoaderScopeSpecEncoder {

    private
    val scopes = WriteIdentities()

    override fun Encoder.encodeScope(scope: ClassLoaderScopeSpec) {
        val id = scopes.getId(scope)
        if (id != null) {
            writeSmallInt(id)
        } else {
            val newId = scopes.putInstance(scope)
            writeSmallInt(newId)
            when (val parent = scope.parent) {
                null -> {
                    writeBoolean(false)
                }

                else -> {
                    writeBoolean(true)
                    encodeScope(parent)
                }
            }
            writeString(scope.name)
            writeOrigin(scope.origin)
            writeNullableHashCode(scope.localImplementationHash)
            encodeClassPath(scope.localClassPath)
            encodeClassPath(scope.exportClassPath)
        }
    }

    protected open fun Encoder.encodeClassPath(classPath: ClassPath) {
        writeClassPath(classPath)
    }

    private
    fun Encoder.writeOrigin(origin: ClassLoaderScopeOrigin?) {
        when (origin) {
            is ClassLoaderScopeOrigin.Script -> {
                writeBoolean(true)
                origin.let {
                    writeString(it.fileName)
                    writeString(it.longDisplayName.displayName)
                    writeString(it.shortDisplayName.displayName)
                }
            }

            else -> {
                writeBoolean(false)
            }
        }
    }

    private
    fun Encoder.writeNullableHashCode(hashCode: HashCode?) {
        if (hashCode == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeHashCode(hashCode)
        }
    }

    protected
    fun Encoder.writeHashCode(hashCode: HashCode) {
        writeBinary(hashCode.toByteArray())
    }
}


internal
open class InlineClassLoaderScopeSpecDecoder : ClassLoaderScopeSpecDecoder {

    private
    val scopes = ReadIdentities()

    override fun Decoder.decodeScope(): ClassLoaderScopeSpec = decodePreservingIdentity(scopes) { id ->
        val parent = if (readBoolean()) {
            decodeScope()
        } else {
            null
        }

        val name = readString()
        val origin = readOrigin()
        val localImplementationHash = readNullableHashCode()
        val localClassPath = decodeClassPath()
        val exportClassPath = decodeClassPath()

        val newScope = ClassLoaderScopeSpec(parent, name, origin).apply {
            this.localClassPath = localClassPath
            this.localImplementationHash = localImplementationHash
            this.exportClassPath = exportClassPath
        }
        scopes.putInstance(id, newScope)
        newScope
    }

    protected open fun Decoder.decodeClassPath(): ClassPath =
        readClassPath()

    private
    fun Decoder.readOrigin() = if (readBoolean()) {
        ClassLoaderScopeOrigin.Script(
            readString(),
            Describables.of(readString()),
            Describables.of(readString())
        )
    } else {
        null
    }

    private
    fun Decoder.readNullableHashCode() = if (readBoolean()) {
        readHashCode()
    } else {
        null
    }

    protected
    fun Decoder.readHashCode(): HashCode = HashCode.fromBytes(readBinary())
}
