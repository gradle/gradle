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

package org.gradle.kotlin.dsl.provider

import org.gradle.internal.buildoption.InternalOption
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory

internal object KotlinDslInternalOptions {
    // Disables build caching for both Kotlin script compilation AND accessor generation
    private val CACHING_DISABLED_PROPERTY: InternalOption<Boolean> =
        InternalOptions.ofBoolean("org.gradle.internal.kotlin-script-caching-disabled", false)
    private val CACHING_DISABLED_REASON: CachingDisabledReason =
        CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Caching of Kotlin script compilation and Kotlin DSL accessors generation disabled by property")

    // Disables build caching only for accessor generation (overridden by CACHING_DISABLED_PROPERTY)
    private val ACCESSOR_CACHING_DISABLED_PROPERTY: InternalOption<Boolean> =
        InternalOptions.ofBoolean("org.gradle.internal.kotlin-script-accessors-caching-disabled", true)
    private val ACCESSOR_CACHING_DISABLED_REASON: CachingDisabledReason =
        CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Build caching of Kotlin script accessor generation disabled by property")

    /**
     * Returns a reason why the script and accessor generation caching is disabled, or `null` if there is none
     */
    fun cachingDisabledReason(internalOptions: InternalOptions): CachingDisabledReason? =
        if (internalOptions.getBoolean(CACHING_DISABLED_PROPERTY)) CACHING_DISABLED_REASON else null

    /**
     * Returns a reason why specifically the accessor generation is disabled, or `null` if there is none
     */
    fun accessorCachingDisabledReason(internalOptions: InternalOptions): CachingDisabledReason? = when {
        internalOptions.getBoolean(CACHING_DISABLED_PROPERTY) -> CACHING_DISABLED_REASON
        internalOptions.getBoolean(ACCESSOR_CACHING_DISABLED_PROPERTY) -> ACCESSOR_CACHING_DISABLED_REASON
        else -> null
    }
}
