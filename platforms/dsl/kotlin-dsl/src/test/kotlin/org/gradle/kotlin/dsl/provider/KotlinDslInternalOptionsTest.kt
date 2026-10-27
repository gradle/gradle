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

package org.gradle.kotlin.dsl.provider

import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory.NOT_CACHEABLE
import org.gradle.kotlin.dsl.provider.KotlinDslInternalOptions.accessorCachingDisabledReason
import org.gradle.kotlin.dsl.provider.KotlinDslInternalOptions.cachingDisabledReason

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class KotlinDslInternalOptionsTest {

    // Values are copied to make sure no accidental drift in property names and messages can happen
    private val cachingDisabledProperty = "org.gradle.internal.kotlin-script-caching-disabled"
    private val cachingDisabledMessage = "Caching of Kotlin script compilation and Kotlin DSL accessors generation disabled by property"
    private val accessorCachingDisabledProperty = "org.gradle.internal.kotlin-script-accessors-caching-disabled"
    private val accessorCachingDisabledMessage = "Build caching of Kotlin script accessor generation disabled by property"

    @Test
    fun `script caching is enabled by default`() {
        assertThat(
            cachingDisabledReason(optionsWith(cachingDisabledProperty to false)),
            nullValue()
        )
    }

    @Test
    fun `script caching can be disabled`() {
        assertReason(
            cachingDisabledReason(optionsWith(cachingDisabledProperty to true)),
            cachingDisabledMessage
        )
    }

    @Test
    fun `accessor caching is disabled by default`() {
        assertReason(
            accessorCachingDisabledReason(optionsWith(cachingDisabledProperty to false)),
            accessorCachingDisabledMessage
        )
    }

    @Test
    fun `accessor caching can be enabled`() {
        assertThat(
            accessorCachingDisabledReason(optionsWith(cachingDisabledProperty to false, accessorCachingDisabledProperty to false)),
            nullValue()
        )
    }

    @Test
    fun `disabling script caching also disables accessor caching`() {
        assertReason(
            accessorCachingDisabledReason(optionsWith(cachingDisabledProperty to true, accessorCachingDisabledProperty to false)),
            cachingDisabledMessage
        )
    }

    @Test
    fun `script caching disabled takes precedence over accessor caching`() {
        assertReason(
            accessorCachingDisabledReason(optionsWith(cachingDisabledProperty to true, accessorCachingDisabledProperty to true)),
            cachingDisabledMessage
        )
    }

    private
    fun optionsWith(vararg settings: Pair<String, Boolean>): InternalOptions =
        DefaultInternalOptions(settings.associate { (name, disabled) -> name to disabled.toString() })

    private
    fun assertReason(reason: CachingDisabledReason?, expectedMessage: String) {
        assertThat(reason?.category, equalTo(NOT_CACHEABLE))
        assertThat(reason?.message, equalTo(expectedMessage))
    }
}
