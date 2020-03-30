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

import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import java.io.File


internal
sealed class InstantExecutionCacheFingerprint {

    data class TaskInputs(
        val taskPath: String,
        val fileSystemInputs: FileCollectionInternal,
        val fileSystemInputsFingerprint: CurrentFileCollectionFingerprint
    ) : InstantExecutionCacheFingerprint()

    data class InputFile(
        val file: File,
        val hash: HashCode?
    ) : InstantExecutionCacheFingerprint()

    data class ValueSource(
        val obtainedValue: ObtainedValue
    ) : InstantExecutionCacheFingerprint()
}


internal
typealias ObtainedValue = ValueSourceProviderFactory.Listener.ObtainedValue<Any, ValueSourceParameters>
