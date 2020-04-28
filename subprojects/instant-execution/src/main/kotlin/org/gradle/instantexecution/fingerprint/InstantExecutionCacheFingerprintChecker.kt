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
import org.gradle.api.internal.GeneratedSubclasses.unpackType
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.internal.hash.HashCode
import org.gradle.internal.util.NumberUtil.ordinal
import java.io.File


internal
typealias InvalidationReason = String


internal
class InstantExecutionCacheFingerprintChecker(private val host: Host) {

    interface Host {
        val allInitScripts: List<File>
        fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode
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
                    if (currentFingerprint != fileSystemInputsFingerprint) {
                        // TODO: summarize what has changed (see https://github.com/gradle/instant-execution/issues/282)
                        return "an input to task '$taskPath' has changed"
                    }
                }
                is InstantExecutionCacheFingerprint.InputFile -> input.run {
                    if (fileHasChanged(file, hash)) {
                        return "file '${displayNameOf(file)}' has changed"
                    }
                }
                is InstantExecutionCacheFingerprint.ValueSource -> input.run {
                    checkFingerprintValueIsUpToDate(obtainedValue)?.let { reason ->
                        return reason
                    }
                }
                is InstantExecutionCacheFingerprint.InitScripts -> input.run {
                    checkInitScriptsAreUpToDate(scripts)?.let { reason ->
                        return reason
                    }
                }
                else -> throw IllegalStateException("Unexpected instant execution cache fingerprint: $input")
            }
        }
    }

    private
    fun checkInitScriptsAreUpToDate(scripts: List<InstantExecutionCacheFingerprint.InputFile>): InvalidationReason? {
        val previous = scripts.iterator()
        val current = host.allInitScripts.iterator()
        var index = 1
        while (current.hasNext()) {
            val currentScript = current.next()
            if (previous.hasNext()) {
                val (previousScript, previousHash) = previous.next()
                if (fileHasChanged(currentScript, previousHash)) {
                    if (previousScript == currentScript) {
                        return "init script '${displayNameOf(currentScript)}' has changed"
                    }
                    return "content of ${ordinal(index)} init script, '${displayNameOf(currentScript)}', has changed"
                }
                index += 1
                continue
            }
            if (current.hasNext()) {
                return "init script '${displayNameOf(currentScript)}' and more have been added"
            }
            return "init script '${displayNameOf(currentScript)}' has been added"
        }
        if (previous.hasNext()) {
            val (removedScript, _) = previous.next()
            if (previous.hasNext()) {
                return "init script '${displayNameOf(removedScript)}' and more have been removed"
            }
            return "init script '${displayNameOf(removedScript)}' has been removed"
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
    fun fileHasChanged(file: File, originalHash: HashCode?) =
        host.hashCodeOf(file) != originalHash

    private
    fun displayNameOf(file: File) =
        host.displayNameOf(file)

    private
    fun buildLogicInputHasChanged(valueSource: ValueSource<Any, ValueSourceParameters>): InvalidationReason =
        (valueSource as? Describable)?.let {
            it.displayName + " has changed"
        } ?: "a build logic input of type '${unpackType(valueSource).simpleName}' has changed"
}
