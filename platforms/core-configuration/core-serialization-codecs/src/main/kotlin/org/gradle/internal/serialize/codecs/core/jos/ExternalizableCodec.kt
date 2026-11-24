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

package org.gradle.internal.serialize.codecs.core.jos

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import java.io.Externalizable

/**
 * This codec forwards `Externalizable` protocol into Configuration Cache serialization.
 */
object ExternalizableCodec : Codec<Externalizable> {
    override suspend fun WriteContext.encode(value: Externalizable) {
        encodePreservingIdentityOf(value) {
            val beanType = value.javaClass
            writeClass(beanType)
            val record = recordWritingOf(beanType, value)
            record.run { playback() }
        }
    }

    private fun recordWritingOf(beanType: Class<Externalizable>, value: Externalizable): RecordingObjectOutputStream =
        RecordingObjectOutputStream(beanType, value).also { recordingObjectOutputStream ->
            value.writeExternal(recordingObjectOutputStream)
        }

    override suspend fun ReadContext.decode(): Externalizable =
        decodePreservingIdentity { id ->
            decodingBeanWithId(id) { bean, _, beanStateReader ->
                val objectInputStream = ObjectInputStreamAdapter(bean, beanStateReader, this)
                (bean as Externalizable).readExternal(objectInputStream)
            } as Externalizable
        }
}
