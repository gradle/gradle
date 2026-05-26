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
 *
 * The lookup helper [org.gradle.internal.serialize.graph.findCodecThatWidensIncompatibly]
 * and the reporting helpers
 * [org.gradle.internal.serialize.graph.reportIfIncompatibleRoundtrip] and
 * [org.gradle.internal.serialize.graph.reportIfUnsupportedPropertyValueType]
 * live in `WideningTypeSerializationFailureHelper.kt` and consume this interface.
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
