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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.internal.cc.impl.ConfigurationCacheStateFile
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore
import org.gradle.internal.cc.impl.serialize.ClassLoaderScopeSpec
import org.gradle.internal.cc.impl.serialize.ClassLoaderScopeSpecDecoder
import org.gradle.internal.cc.impl.serialize.ClassLoaderScopeSpecEncoder
import org.gradle.internal.cc.impl.serialize.InlineClassLoaderScopeSpecDecoder
import org.gradle.internal.cc.impl.serialize.InlineClassLoaderScopeSpecEncoder
import org.gradle.internal.extensions.stdlib.invert
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.PositionAwareEncoder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.util.IdentityHashMap


/**
 * Manages the encoding and decoding of [ClassLoaderScope specifications][org.gradle.internal.cc.impl.serialize.ClassLoaderScopeSpec].
 */
@ServiceScope(Scope.BuildTree::class)
internal
interface ClassLoaderScopesFingerprintController {

    /**
     * When storing a configuration cache entry:
     * 1. [prepareForWriting] is called before any [ClassLoaderScopeSpec] encoding needs to happen
     * 2. then [encoder] is called for as many contexts as necessary
     * 3. finally [commit] is called
     */
    fun prepareForWriting(parameters: ConfigurationCacheFingerprintStartParameters)
    fun encoder(): ClassLoaderScopeSpecEncoder
    fun commit(stateFile: ConfigurationCacheStateFile)

    /**
     * When loading a configuration cache entry:
     * 1. [checkClassLoaderScopes] is called before any [ClassLoaderScopeSpec] decoding needs to happen,
     *    to ensure all stored class paths are still valid
     * 2. then [decoder] is called for as many contexts as necessary
     */
    fun checkClassLoaderScopes(decoderSupplier: () -> Decoder): InvalidationReason?
    fun decoder(): ClassLoaderScopeSpecDecoder
}


/**
 * Currently doesn't validate any class paths since IP requires a more sophisticated implementation
 * to handle incremental updates to project scopes.
 *
 * TODO:isolated validate class paths
 */
internal
class IsolatedProjectsClassLoaderScopesFingerprintController : ClassLoaderScopesFingerprintController {

    override fun prepareForWriting(parameters: ConfigurationCacheFingerprintStartParameters) = Unit

    override fun encoder(): ClassLoaderScopeSpecEncoder = InlineClassLoaderScopeSpecEncoder()

    override fun commit(stateFile: ConfigurationCacheStateFile) = Unit

    override fun checkClassLoaderScopes(decoderSupplier: () -> Decoder): InvalidationReason? = null

    override fun decoder(): ClassLoaderScopeSpecDecoder = InlineClassLoaderScopeSpecDecoder()
}


/**
 * Write all specs to a single, separate fingerprint file so all required class paths can be checked before any class is loaded.
 *
 * TODO:configuration-cache discard service state after entry has been fully loaded
 */
internal
class ConfigurationCacheClassLoaderScopesFingerprintController : ClassLoaderScopesFingerprintController {

    private
    var writingState: WritingState = NotWriting

    private
    var scopeSpecs: Map<Int, ClassLoaderScopeSpec>? = null

    override fun prepareForWriting(parameters: ConfigurationCacheFingerprintStartParameters) {
        writingState = writingState.prepare(parameters)
    }

    override fun commit(stateFile: ConfigurationCacheStateFile) {
        val commited = writingState.commit(stateFile)
        writingState = commited
        scopeSpecs = commited.encodedScopes
    }

    override fun checkClassLoaderScopes(decoderSupplier: () -> Decoder): InvalidationReason? {
        decoderSupplier().let { decoder ->
            require(decoder is Closeable)
            decoder.useToRun {
                val result = HashMap<Int, ClassLoaderScopeSpec>()
                InlineClassLoaderScopeSpecDecoder().run {
                    while (true) {
                        val id = readSmallInt()
                        if (id == 0) {
                            break
                        }
                        // TODO:configuration-cache validate class paths
                        val scopeSpec = decodeScope()
                        result[id] = scopeSpec
                    }
                }
                scopeSpecs = result
            }
        }
        return null
    }

    override fun encoder(): ClassLoaderScopeSpecEncoder =
        writingState.encoder()

    override fun decoder(): ClassLoaderScopeSpecDecoder =
        when (val scopes = scopeSpecs) {
            null -> error("Classloader scopes have not been stored to or loaded from the cache.")
            else -> SharedClassLoaderScopeSpecDecoder(scopes)
        }

    private
    abstract class WritingState {

        open fun prepare(parameters: ConfigurationCacheFingerprintStartParameters): Writing =
            illegalStateFor("prepare")

        open fun encoder(): ClassLoaderScopeSpecEncoder =
            illegalStateFor("encoder")

        open fun commit(stateFile: ConfigurationCacheStateFile): Committed =
            illegalStateFor("commit")

        private
        fun illegalStateFor(operation: String): Nothing =
            error("'$operation' is illegal while in '${javaClass.simpleName}' state.")
    }

    private
    object NotWriting : WritingState() {

        override fun prepare(parameters: ConfigurationCacheFingerprintStartParameters): Writing =
            parameters.assignClassLoaderScopesFile().let { spoolFile ->
                Writing(spoolFile, parameters.encoderFor(spoolFile))
            }
    }

    private
    class Writing(
        val spoolFile: ConfigurationCacheStateStore.StateFile,
        encoder: PositionAwareEncoder
    ) : WritingState() {

        private
        val sharedClassLoaderScopeSpecEncoder = SharedClassLoaderScopeSpecEncoder(encoder)

        override fun prepare(parameters: ConfigurationCacheFingerprintStartParameters): Writing =
            this

        override fun encoder(): ClassLoaderScopeSpecEncoder =
            sharedClassLoaderScopeSpecEncoder

        override fun commit(stateFile: ConfigurationCacheStateFile): Committed {
            sharedClassLoaderScopeSpecEncoder.finish()
            stateFile.moveFrom(spoolFile.file)
            return Committed(sharedClassLoaderScopeSpecEncoder.encodedScopes())
        }
    }

    private
    data class Committed(val encodedScopes: Map<Int, ClassLoaderScopeSpec>) : WritingState()

    private
    class SharedClassLoaderScopeSpecEncoder(val encoder: PositionAwareEncoder) : ClassLoaderScopeSpecEncoder {

        private
        val scopeIds = IdentityHashMap<ClassLoaderScopeSpec, Int>()

        private
        val specEncoder = InlineClassLoaderScopeSpecEncoder()

        override fun Encoder.encodeScope(scope: ClassLoaderScopeSpec) {
            val (id, isNew) = idFor(scope)
            if (isNew) {
                doEncode(id, scope)
            }
            writeSmallInt(id)
        }

        fun finish() {
            encoder.writeSmallInt(0)
            (encoder as Closeable).close()
        }

        fun encodedScopes(): Map<Int, ClassLoaderScopeSpec> =
            scopeIds.invert()

        private
        fun idFor(scope: ClassLoaderScopeSpec): Pair<Int, Boolean> =
            synchronized(scopeIds) {
                val id = scopeIds[scope]
                if (id != null) {
                    id to false
                } else {
                    // Avoid zero as an id since it marks the end of file
                    val newId = scopeIds.size + 1
                    scopeIds.put(scope, newId)
                    newId to true
                }
            }

        private
        fun doEncode(id: Int, scope: ClassLoaderScopeSpec) {
            synchronized(encoder) {
                encoder.run {
                    specEncoder.run {
                        writeSmallInt(id)
                        encodeScope(scope)
                    }
                }
            }
        }
    }

    private
    class SharedClassLoaderScopeSpecDecoder(
        private val encodedScopes: Map<Int, ClassLoaderScopeSpec>
    ) : ClassLoaderScopeSpecDecoder {

        override fun Decoder.decodeScope(): ClassLoaderScopeSpec =
            encodedScopes.getValue(readSmallInt())
    }
}
