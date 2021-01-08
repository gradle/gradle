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

import org.junit.Test
import java.util.concurrent.Callable


class CallableCodecTest : AbstractFunctionalTypeTest() {

    @Test
    fun `defers evaluation of Callable objects`() {
        assertDeferredEvaluationOf(callable()) {
            call()
        }
    }

    @Test
    fun `defers evaluation of dynamic Callable fields`() {
        assertDeferredEvaluationOf(BeanOf(callable())) {
            value.call()
        }
    }

    @Test
    fun `defers evaluation of static Callable fields`() {
        assertDeferredEvaluationOf(CallableBean(callable())) {
            value.call()
        }
    }

    private
    fun callable() = Callable { Runtime.value }

    data class CallableBean(val value: Callable<Any?>)
}
