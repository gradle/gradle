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

import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.SystemPropertyValueSource
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.vfs.VirtualFileSystem
import java.io.File


internal
typealias InvalidationReason = String


internal
class InstantExecutionFingerprintChecker(
    private val virtualFileSystem: VirtualFileSystem,
    private val valueSourceProviderFactory: ValueSourceProviderFactory
) {

    object FingerprintEncoder {
        suspend fun WriteContext.encodeFingerprintOf(inputs: InstantExecutionCacheInputs) {
            inputs.run {
                writeCollection(inputFiles)
                writeCollection(obtainedValues)
            }
        }
    }

    suspend fun ReadContext.checkFingerprint(): InvalidationReason? =
        (checkFingerprintOfInputFiles() ?: checkFingerprintOfObtainedValues())

    private
    suspend fun ReadContext.checkFingerprintOfInputFiles(): InvalidationReason? {
        readCollection {
            val (inputFile, hashCode) = readNonNull<InstantExecutionCacheInputs.InputFile>()
            if (hashCodeOf(inputFile) != hashCode) {
                // TODO: log some debug info
                return "a configuration file has changed"
            }
        }
        return null
    }

    private
    suspend fun ReadContext.checkFingerprintOfObtainedValues(): InvalidationReason? {
        readCollection {
            val obtainedValue = readNonNull<ObtainedValue>()
            checkFingerprintValueIsUpToDate(obtainedValue)?.let { reason ->
                return reason
            }
        }
        return null
    }

    private
    fun hashCodeOf(inputFile: File) = virtualFileSystem.hashCodeOf(inputFile)

    private
    fun checkFingerprintValueIsUpToDate(obtainedValue: ObtainedValue): InvalidationReason? = obtainedValue.run {
        when (valueSourceType) {
            SystemPropertyValueSource::class.java -> {
                // Special case system properties to get them from the host because
                // this check happens too early in the process, before the system properties
                // passed in the command line have been propagated.
                val propertyName = valueSourceParameters
                    .uncheckedCast<SystemPropertyValueSource.Parameters>()
                    .propertyName
                    .get()
                if (value.get() != System.getProperty(propertyName)) {
                    "system property '$propertyName' has changed"
                } else {
                    null
                }
            }
            else -> {
                val valueSource = instantiateValueSource()
                if (value.get() != valueSource.obtain()) {
                    "a build logic input has changed"
                } else {
                    null
                }
            }
        }
    }

    private
    fun ObtainedValue.instantiateValueSource(): ValueSource<Any, ValueSourceParameters> =
        (valueSourceProviderFactory as DefaultValueSourceProviderFactory).instantiateValueSource(
            valueSourceType,
            valueSourceParametersType,
            valueSourceParameters
        )
}
