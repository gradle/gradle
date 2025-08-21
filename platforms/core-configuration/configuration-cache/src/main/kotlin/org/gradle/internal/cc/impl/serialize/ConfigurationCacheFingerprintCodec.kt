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

package org.gradle.internal.cc.impl.serialize

import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprint
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.encodeBean


/**
 * Writes [ConfigurationCacheFingerprint] nodes without preserving identity.
 */
internal
object ConfigurationCacheFingerprintCodec : Codec<ConfigurationCacheFingerprint> {

    override suspend fun WriteContext.encode(value: ConfigurationCacheFingerprint) {
        encodeBean(value)
    }

    override suspend fun ReadContext.decode(): ConfigurationCacheFingerprint =
        decodeBean().uncheckedCast()
}
