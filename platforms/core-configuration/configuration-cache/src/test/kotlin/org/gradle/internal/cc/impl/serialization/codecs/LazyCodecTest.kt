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

package org.gradle.internal.cc.impl.serialization.codecs

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


/**
 * [Lazy] values support Java serialization via a custom `writeReplace` method that forces their evaluation.
 */
class LazyCodecTest : AbstractFunctionalTypeTest() {

    @Test
    fun `forces evaluation of Lazy objects`() {
        assertEagerEvaluationOf(lazy()) {
            value
        }
    }

    @Test
    fun `forces evaluation of dynamic Lazy fields`() {
        assertEagerEvaluationOf(BeanOf(lazy())) {
            value.value
        }
    }

    @Test
    fun `forces evaluation of static Lazy fields`() {
        assertEagerEvaluationOf(LazyBean(lazy())) {
            value.value
        }
    }

    @Test
    fun `forces evaluation of by lazy properties`() {
        assertEagerEvaluationOf(ByLazyBean()) {
            value
        }
    }

    /**
     * Demonstrates the uninitialized-Lazy bypass in
     * `BeanPropertyWriter.reportIfUnsupportedKotlinDelegate`: when a `by lazy`
     * delegate has not been forced before configuration cache store, the widening
     * check is skipped without forcing the delegate itself. The encode pass then
     * forces the lazy via [Lazy]'s `writeReplace` and the cached value is the
     * forced one — verified by reading `value` after roundtrip and seeing the
     * pre-roundtrip [Runtime.value].
     */
    @Test
    fun `uninitialized by lazy delegate is not forced by the widening check, only by the codec's writeReplace`() {
        val bean = ByLazyBean()
        assertThat(
            "precondition: the delegate is uninitialized at the moment we hand it to the configuration cache",
            bean.delegateIsInitialized,
            equalTo(false)
        )
        Runtime.value = "before"
        val roundtripped = configurationCacheRoundtripOf(bean)

        Runtime.value = "after"
        assertThat(
            "value must reflect the store-time Runtime.value, proving writeReplace forced the lazy during encode",
            roundtripped.value,
            equalTo("before")
        )
    }

    private
    fun lazy(): Lazy<Any?> = lazy { Runtime.value }

    data class LazyBean(val value: Lazy<Any?>)

    class ByLazyBean {
        private val delegate = lazy { Runtime.value }
        // Compiles to a synthetic `value$delegate` field holding `delegate`.
        val value by delegate

        val delegateIsInitialized: Boolean
            get() = delegate.isInitialized()
    }
}
