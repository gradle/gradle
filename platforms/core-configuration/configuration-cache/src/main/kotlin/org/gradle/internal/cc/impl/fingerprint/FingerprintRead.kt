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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.internal.cc.impl.ConfigurationCacheBuildTreeIO
import org.gradle.internal.cc.impl.ConfigurationCacheStateFile
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.serialize.graph.withIsolate


/**
 * Reads [stateFile] as a fingerprint, running [action] in an isolate that can decode fingerprint values.
 */
internal
fun <T> ConfigurationCacheBuildTreeIO.readFingerprintFrom(
    stateFile: ConfigurationCacheStateFile,
    isolateOwner: IsolateOwner,
    action: suspend ReadContext.(ConfigurationCacheFingerprintController.Host) -> T
): T =
    readFingerprintFrom(
        stateFile.stateFile.name,
        decoderFor(stateFile.stateType, stateFile::inputStream),
        isolateOwner,
        action
    )


/**
 * Reads a fingerprint from an already opened [decoder]
 */
internal
fun <T> ConfigurationCacheBuildTreeIO.readFingerprintFrom(
    stateFileName: String,
    decoder: Decoder,
    isolateOwner: IsolateOwner,
    action: suspend ReadContext.(ConfigurationCacheFingerprintController.Host) -> T
): T =
    runReadOperation(stateFileName, decoder) { codecs ->
        withIsolate(isolateOwner, codecs.fingerprintTypesCodec()) {
            action(FingerprintControllerHost(isolateOwner))
        }
    }


private
class FingerprintControllerHost(
    private val isolateOwner: IsolateOwner
) : ConfigurationCacheFingerprintController.Host {
    override val valueSourceProviderFactory: ValueSourceProviderFactory
        get() = isolateOwner.serviceOf()
}
