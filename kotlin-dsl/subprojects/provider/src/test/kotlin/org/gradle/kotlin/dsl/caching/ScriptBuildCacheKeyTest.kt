/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.caching

import org.gradle.kotlin.dsl.cache.ScriptBuildCacheKey

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class ScriptBuildCacheKeyTest {

    @Test
    fun `it discards the local cache key prefix`() {

        assertThat(
            buildCacheKeyFor("gradle-kotlin-dsl/dx7phw87epyvb7zje5dti3tvl").hashCode,
            equalTo("dx7phw87epyvb7zje5dti3tvl")
        )
    }

    @Test
    fun `toString is the hashCode`() {

        assertThat(
            buildCacheKeyFor("gradle-kotlin-dsl/dx7phw87epyvb7zje5dti3tvl").toString(),
            equalTo("dx7phw87epyvb7zje5dti3tvl")
        )
    }

    private
    fun buildCacheKeyFor(cacheKey: String) =
        ScriptBuildCacheKey("", cacheKey)
}
