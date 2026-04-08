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

package org.gradle.internal.serialize.beans.services

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.provider.MapProperty

/**
 * Types that cannot be used as the value type `T` of a `Property<T>`
 * in the configuration cache.
 *
 * A `Property<T>` where `T` matches one of these types (or a subtype)
 * cannot survive a serialization round-trip: the codec that handles
 * the resolved value produces a different type on deserialization
 * than `Property<T>` expects, causing a confusing type-mismatch error
 * at load time.
 *
 * Used by property codecs to fail fast at store time with a clear
 * diagnostic message.
 */
object UnsupportedPropertyValueTypes {
    private val unsupported: Map<Class<*>, String> = mapOf(
        Configuration::class.java to "Use a @InputFiles ConfigurableFileCollection instead.",
        SourceDirectorySet::class.java to "Use a @InputFiles ConfigurableFileCollection instead.",
    )

    /**
     * Checks whether [valueType] is supported as a property value type
     * for the given [propertyKind] (e.g., `Property`, `ListProperty`, `MapProperty`).
     */
    fun check(valueType: Class<*>, propertyKind: Class<*>): CheckResult {
        val baseResolution = unsupported.entries
            .firstOrNull { it.key.isAssignableFrom(valueType) }
            ?.value
            ?: return CheckResult.Supported

        val resolution = when {
            MapProperty::class.java.isAssignableFrom(propertyKind) ->
                "Avoid using ${valueType.simpleName} as a MapProperty key or value."
            else -> baseResolution
        }

        return CheckResult.Unsupported(resolution)
    }

    /**
     * Result of checking whether a type is supported as a property value type
     * in the configuration cache.
     */
    sealed interface CheckResult {
        /** The type is supported — no action needed. */
        object Supported : CheckResult

        /** The type is unsupported — [resolution] describes what to use instead. */
        class Unsupported(val resolution: String) : CheckResult
    }
}
