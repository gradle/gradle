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

package org.gradle.internal.serialize.graph


/**
 * Decouples [WriteContext.codecForRuntimeType] from any particular codec
 * registry implementation. A codec that can resolve per-type encodings
 * implements this interface; contexts that receive a `Codec<Any?>` at
 * construction query through it via an `as?` cast without naming the
 * concrete codec type, keeping the `.graph` package free of direct
 * dependencies on `.graph.codecs`.
 */
interface CodecLookup {
    /**
     * Returns the encoding registered for the given runtime [type], or null
     * when none matches. The returned value is typed as `Any?` at this layer
     * so callers cast to concrete codec interfaces (for example
     * `WideningCodec<*>`) without forcing this interface to depend on them.
     */
    fun encodingForType(type: Class<*>): Any?
}
