/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution.fingerprint

import org.gradle.api.Describable
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import java.io.File


internal
typealias InvalidationReason = String


internal
class InstantExecutionCacheFingerprintChecker(private val host: Host) {

    interface Host {
        fun fingerprintOf(fileCollection: FileCollectionInternal): CurrentFileCollectionFingerprint
        fun hashCodeOf(file: File): HashCode?
        fun displayNameOf(fileOrDirectory: File): String
        fun instantiateValueSourceOf(obtainedValue: ObtainedValue): ValueSource<Any, ValueSourceParameters>
    }

    suspend fun ReadContext.checkFingerprint(): InvalidationReason? {
        // TODO: log some debug info
        while (true) {
            when (val input = read()) {
                null -> return null
                is InstantExecutionCacheFingerprint.TaskInputs -> input.run {
                    val currentFingerprint = host.fingerprintOf(fileSystemInputs)
                    if (currentFingerprint.hash != fileSystemInputsFingerprint.hash) {
                        // TODO: summarize what has changed (see https://github.com/gradle/instant-execution/issues/282)
                        return "an input to task '$taskPath' has changed"
                    }
                }
                is InstantExecutionCacheFingerprint.InputFile -> input.run {
                    if (host.hashCodeOf(file) != hash) {
                        return "configuration file '${displayNameOf(file)}' has changed"
                    }
                }
                is InstantExecutionCacheFingerprint.ValueSource -> input.run {
                    checkFingerprintValueIsUpToDate(obtainedValue)?.let { reason ->
                        return reason
                    }
                }
                else -> throw IllegalStateException("Unexpected instant execution cache fingerprint: $input")
            }
        }
    }

    private
    fun displayNameOf(file: File) =
        host.displayNameOf(file)

    private
    fun checkFingerprintValueIsUpToDate(obtainedValue: ObtainedValue): InvalidationReason? {
        val valueSource = host.instantiateValueSourceOf(obtainedValue)
        if (obtainedValue.value.get() != valueSource.obtain()) {
            return buildLogicInputHasChanged(valueSource)
        }
        return null
    }

    private
    fun buildLogicInputHasChanged(valueSource: ValueSource<Any, ValueSourceParameters>): InvalidationReason =
        (valueSource as? Describable)?.let {
            it.displayName + " has changed"
        } ?: "a build logic input of type '${valueSource.javaClass.simpleName}' has changed"
}
