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

import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.FileContentValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.internal.hash.HashCode
import org.gradle.internal.vfs.VirtualFileSystem
import java.io.File


internal
typealias ObtainedValue = ValueSourceProviderFactory.Listener.ObtainedValue<Any, ValueSourceParameters>


internal
data class InstantExecutionCacheFingerprint(
    val inputFiles: List<InputFile>,
    val obtainedValues: List<ObtainedValue>
) {
    internal
    data class InputFile(
        val file: File,
        val hashCode: HashCode?
    )
}


internal
class InstantExecutionCacheInputs(
    val virtualFileSystem: VirtualFileSystem
) : ValueSourceProviderFactory.Listener {

    val inputFiles = mutableListOf<InstantExecutionCacheFingerprint.InputFile>()

    val obtainedValues = mutableListOf<ObtainedValue>()

    val fingerprint
        get() = InstantExecutionCacheFingerprint(inputFiles, obtainedValues)

    override fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.Listener.ObtainedValue<T, P>
    ) {
        when (val parameters = obtainedValue.valueSourceParameters) {
            is FileContentValueSource.Parameters -> {
                parameters.file.orNull?.asFile?.let { file ->
                    // TODO - consider the potential race condition in computing the hash code here
                    inputFiles.add(
                        InstantExecutionCacheFingerprint.InputFile(
                            file,
                            virtualFileSystem.hashCodeOf(file)
                        )
                    )
                }
            }
            else -> {
                obtainedValues.add(obtainedValue.uncheckedCast())
            }
        }
    }
}


internal
fun VirtualFileSystem.hashCodeOf(file: File): HashCode? =
    readRegularFileContentHash(file.path) { hashCode -> hashCode }
        .orElse(null)
