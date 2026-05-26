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

package org.gradle.internal.serialize.graph.codecs

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.WriteContext


/**
 * A codec whose decode contract returns a type that may be strictly wider
 * than the runtime types of values it encodes.
 *
 * Implementing this interface opts the codec into store-time roundtrip-type
 * validation: when a value flows into a bean field, the field's declared type
 * is checked against `decodedType`.  If the decoded type cannot be assigned to
 * the field, serialization fails immediately rather than producing a cache
 * entry that cannot be loaded.
 *
 * Example: `ConfigurationCodec` extends `FileCollectionCodec` and is bound
 * for `Configuration`, but its `decode` (inherited from `FileCollectionCodec`)
 * always returns a `FileCollectionInternal`.  A field declared as
 * `Configuration` cannot accept that decoded value, so `ConfigurationCodec`
 * opts in.
 */
interface WideningCodec<T : Any> : Codec<T> {
    /** The exact type produced by `decode`. */
    val decodedType: Class<T>

    /**
     * The type displayed in error messages when the decoded value cannot be
     * assigned to a field. Defaults to [decodedType]. Override when
     * [decodedType] is an internal type and a public-facing name reads better
     * (for example, `ConfigurationCodec` uses `decodedType = FileCollectionInternal`
     * and `publicDecodedType = FileCollection`).
     */
    val publicDecodedType: Class<*>
        get() = decodedType

    /**
     * Human-readable guidance shown alongside the error when a field cannot
     * accept the decoded type. Each [WideningCodec] must supply a concrete
     * suggestion the user can act on (typically: which supported type to use
     * instead).
     */
    val wideningFix: String
}


/**
 * Returns the [WideningCodec] registered for [runtimeType] when its decoded
 * type cannot be assigned to [declaredType] — meaning a value of [runtimeType]
 * flowing into a slot typed [declaredType] cannot survive the configuration
 * cache roundtrip. Returns null in three cases:
 *
 * - no codec is registered for [runtimeType],
 * - the registered codec is not a [WideningCodec] (it roundtrips to its
 *   declared type, no widening occurs), or
 * - the slot's declared type can accept the codec's `decodedType` (the
 *   load-side value fits the slot).
 *
 * Centralises the codec-lookup core shared by every store-time roundtrip-type
 * check (bean fields, record components, lambda parameters, `Property<T>`
 * value types, Kotlin delegates). Callers layer their own carve-outs and
 * reporting on top of the returned codec.
 *
 * Pass [runtimeType] explicitly when the value's runtime class is more specific
 * than the slot's declared type (the common case for bean fields). Omit it when
 * the slot's declared type IS the only type signal available (the lambda and
 * `Property<T>` cases) — it then defaults to [declaredType].
 */
fun WriteContext.findCodecThatWidensIncompatibly(
    declaredType: Class<*>,
    runtimeType: Class<*> = declaredType
): WideningCodec<*>? {
    val widening = codecForRuntimeType(runtimeType) as? WideningCodec<*> ?: return null
    if (declaredType.isAssignableFrom(widening.decodedType)) return null
    return widening
}
