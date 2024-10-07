/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.graph.codecs

import org.gradle.internal.ShareableObject
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext


/**
 * Forces the given [sharedBean] to be encoded via the [ShareableObjectCodec] regardless of its type.
 */
class ShareableObjectSpec(val sharedBean: Any)

/**
 * Handles objects that are themselves [ShareableObject]s.
 */
object ShareableObjectCodec : Codec<ShareableObject> {
    override suspend fun WriteContext.encode(value: ShareableObject) =
        ShareableObjectSpecCodec.run {
            encode(ShareableObjectSpec(value))
        }

    override suspend fun ReadContext.decode(): ShareableObject =
        ShareableObjectSpecCodec.run {
            decode().sharedBean.uncheckedCast()
        }
}


/**
 * Handles objects that are not [ShareableObject]s, so need to be wrapped into [ShareableObjectSpec]s.
 */
object ShareableObjectSpecCodec : Codec<ShareableObjectSpec> {
    override suspend fun WriteContext.encode(value: ShareableObjectSpec) {
        writeShareableObject(value.sharedBean) { shared ->
            BeanCodec.run {
                encode(shared)
            }
        }
    }

    override suspend fun ReadContext.decode(): ShareableObjectSpec =
        readShareableObject {
            BeanCodec.run {
                decode()
            }
        }.let(::ShareableObjectSpec)
}

