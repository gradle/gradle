/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.cc.base.exceptions.ConfigurationCacheThrowable
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprint
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.encodeBean
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.withPropertyTrace


/**
 * Signals that a configuration cache fingerprint entry could not be deserialized while checking whether the entry can
 * be reused. The [reason] describes the failure so the entry can be invalidated with a message that points at its cause.
 */
internal open class FingerprintDeserializationException(
    val reason: StructuredMessage,
    cause: Throwable
) : Exception(reason.render(), cause), ConfigurationCacheThrowable


private inline fun <T> ReadContext.tryReading(
    read: () -> T,
    onFailure: (Throwable) -> FingerprintDeserializationException,
): T =
    try {
        read()
    } catch (e: Exception) {
        if (isIntegrityCheckEnabled || e is ConfigurationCacheThrowable) throw e
        throw onFailure(e)
    }


private fun loadFailureReason(cause: Throwable, describeInput: StructuredMessage.Builder.() -> Unit): StructuredMessage =
    StructuredMessage.build {
        text("the value of ")
        describeInput()
        text(" could not be loaded")
        generateSequence(cause) { it.cause }.last().message?.let { text(": $it") }
    }


/**
 * Serializes a [ConfigurationCacheFingerprint.ValueSource] so that a failure to load the fingerprinted
 * value can be attributed to the value source that produced it.
 */
internal object ValueSourceFingerprintCodec : Codec<ConfigurationCacheFingerprint.ValueSource> {

    override suspend fun WriteContext.encode(value: ConfigurationCacheFingerprint.ValueSource) {
        writeClass(value.obtainedValue.valueSourceType)
        encodeBean(value.obtainedValue)
    }

    override suspend fun ReadContext.decode(): ConfigurationCacheFingerprint.ValueSource {
        val valueSourceType = readClass()
        val obtainedValue = withPropertyTrace(PropertyTrace.BuildLogicClass(valueSourceType.name)) {
            tryReading(
                { decodeBean() },
                { ValueSourceFingerprintLoadFailure(valueSourceType, it) }
            )
        }
        return ConfigurationCacheFingerprint.ValueSource(obtainedValue.uncheckedCast())
    }
}


internal class ValueSourceFingerprintLoadFailure(
    valueSourceType: Class<*>,
    cause: Throwable
) : FingerprintDeserializationException(
    loadFailureReason(cause) {
        text("a build logic input of type ")
        reference(valueSourceType.simpleName)
    },
    cause
)


/**
 * Serializes a [ConfigurationCacheFingerprint.SystemPropertyChanged] so that a failure to load the recorded value
 * can be attributed to the system property.
 */
internal object SystemPropertyChangedFingerprintCodec : Codec<ConfigurationCacheFingerprint.SystemPropertyChanged> {

    override suspend fun WriteContext.encode(value: ConfigurationCacheFingerprint.SystemPropertyChanged) {
        write(value.key)
        write(value.value)
    }

    override suspend fun ReadContext.decode(): ConfigurationCacheFingerprint.SystemPropertyChanged {
        val key = readNonNull<Any>()
        val value = tryReading(
            { read() },
            { SystemPropertyChangedLoadFailure(key, it) }
        )
        return ConfigurationCacheFingerprint.SystemPropertyChanged(key, value)
    }
}


internal class SystemPropertyChangedLoadFailure(
    key: Any,
    cause: Throwable
) : FingerprintDeserializationException(
    loadFailureReason(cause) {
        text("system property ")
        reference(key.toString())
    },
    cause
)
