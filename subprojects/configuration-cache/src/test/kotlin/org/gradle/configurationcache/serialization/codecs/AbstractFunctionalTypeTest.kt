/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat


abstract class AbstractFunctionalTypeTest : AbstractUserTypeCodecTest() {

    protected
    fun <T : Any> assertDeferredEvaluationOf(deferred: T, force: T.() -> Any?) {
        Runtime.value = "before"
        val value = configurationCacheRoundtripOf(deferred)

        Runtime.value = "after"
        assertThat(
            force(value),
            equalTo("after")
        )
    }

    protected
    fun <T : Any> assertEagerEvaluationOf(eager: T, extract: T.() -> Any?) {
        Runtime.value = "before"
        val value = configurationCacheRoundtripOf(eager)

        Runtime.value = "after"
        assertThat(
            extract(value),
            equalTo("before")
        )
    }

    object Runtime {
        private
        val local = ThreadLocal<Any?>()

        var value: Any?
            get() = local.get()
            set(value) = local.set(value)
    }

    data class BeanOf<T>(val value: T)
}
