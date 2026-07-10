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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.internal.properties.DefaultGradlePropertiesController
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.properties.GradlePropertiesController
import org.gradle.api.internal.properties.GradlePropertyScope
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodeBean
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.serviceOf


object GradlePropertiesCodec : Codec<GradleProperties> {

    override suspend fun WriteContext.encode(value: GradleProperties) {
        encodePreservingSharedIdentityOf(value) {
            if (value is DefaultGradlePropertiesController.ScopedGradleProperties) {
                writeBoolean(true)
                write(value.propertyScope)
            } else {
                writeBoolean(false)
                encodeBean(value)
            }
        }
    }

    override suspend fun ReadContext.decode(): GradleProperties {
        return decodePreservingSharedIdentity {
            if (readBoolean()) {
                val propertyScope = readNonNull<GradlePropertyScope>()
                isolate.owner
                    .serviceOf<GradlePropertiesController>()
                    .getGradleProperties(propertyScope)
            } else {
                decodeBean().uncheckedCast()
            }
        }
    }
}
