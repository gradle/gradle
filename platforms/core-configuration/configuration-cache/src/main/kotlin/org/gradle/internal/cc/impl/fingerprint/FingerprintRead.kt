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
import org.gradle.internal.cc.base.serialize.HostServiceProvider
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.base.serialize.service
import org.gradle.internal.cc.impl.ConfigurationCacheBuildTreeIO
import org.gradle.internal.cc.impl.ConfigurationCacheStateFile
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.withIsolate


/**
 * Reads [stateFile] as a fingerprint, running [action] in an isolate that can decode fingerprint values.
 */
internal
fun <T> ConfigurationCacheBuildTreeIO.readFingerprintFrom(
    stateFile: ConfigurationCacheStateFile,
    host: HostServiceProvider,
    action: suspend ReadContext.(ConfigurationCacheFingerprintController.Host) -> T
): T {
    val decoder = decoderFor(stateFile.stateType, stateFile::inputStream)
    return runReadOperation(stateFile.stateFile.name, decoder) { codecs ->
        withIsolate(IsolateOwners.OwnerHost(host), codecs.fingerprintTypesCodec()) {
            action(FingerprintControllerHost(host))
        }
    }
}


private
class FingerprintControllerHost(
    private val host: HostServiceProvider
) : ConfigurationCacheFingerprintController.Host {
    override val valueSourceProviderFactory: ValueSourceProviderFactory
        get() = host.service()
}
