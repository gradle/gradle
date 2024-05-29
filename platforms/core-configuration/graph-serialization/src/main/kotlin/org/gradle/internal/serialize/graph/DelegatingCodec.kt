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

package org.gradle.internal.serialize.graph


/**
 * A codec that delegates to some more general codec, but only for a specific type
 */
class DelegatingCodec<T>(
    private val userTypesCodec: Codec<Any?>,
) : Codec<T> {

    override suspend fun WriteContext.encode(value: T) {
        // Delegate to the other codec
        withCodec(userTypesCodec) {
            write(value)
        }
    }

    override suspend fun ReadContext.decode(): T {
        // Delegate to the other codec
        return withCodec(userTypesCodec) {
            readNonNull()
        }
    }
}
