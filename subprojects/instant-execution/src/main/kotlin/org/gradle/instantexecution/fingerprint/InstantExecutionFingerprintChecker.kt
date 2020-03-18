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
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.internal.hash.HashCode
import java.io.File


internal
typealias InvalidationReason = String


internal
class InstantExecutionFingerprintChecker(private val host: Host) {

    interface Host {
        fun hashCodeForFile(file: File): HashCode?
        fun hashCodeForDirectory(directory: File): HashCode?
        fun displayNameOf(fileOrDirectory: File): String
        fun instantiateValueSourceOf(obtainedValue: ObtainedValue): ValueSource<Any, ValueSourceParameters>
    }

    suspend fun ReadContext.checkFingerprint(): InvalidationReason? {
        // TODO: log some debug info
        while (true) {
            when (val input = read()) {
                is InstantExecutionCacheFingerprint.TaskInputDir -> input.run {
                    if (host.hashCodeForDirectory(directory) != hashCode) {
                        return "directory '${displayNameOf(directory)}', an input to task '$taskPath', has changed"
                    }
                }
                is InstantExecutionCacheFingerprint.InputFile -> input.run {
                    if (host.hashCodeForFile(file) != hashCode) {
                        return "configuration file '${displayNameOf(file)}' has changed"
                    }
                }
                is InstantExecutionCacheFingerprint.ValueSource -> input.run {
                    checkFingerprintValueIsUpToDate(obtainedValue)?.let { reason ->
                        return reason
                    }
                }
                else -> return null
            }
        }
    }

    private
    fun displayNameOf(file: File) =
        host.displayNameOf(file)

    private
    suspend fun ReadContext.checkFingerprintOfObtainedValues(): InvalidationReason? {
        readCollection {
        }
        return null
    }

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
