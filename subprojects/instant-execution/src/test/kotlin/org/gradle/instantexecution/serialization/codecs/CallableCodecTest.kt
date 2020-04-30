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

package org.gradle.instantexecution.serialization.codecs

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.util.concurrent.Callable


class CallableCodecTest : AbstractUserTypeCodecTest() {

    @Test
    fun `defers execution of Callable objects`() {
        Register.value = "before"
        val callable = roundtrip(Callable { Register.value })

        Register.value = "after"
        assertThat(
            callable.call(),
            equalTo("after")
        )
    }

    @Test
    fun `defers execution of Callable fields`() {
        Register.value = "before"
        val callable = roundtrip(BeanOf(Callable { Register.value })).value

        Register.value = "after"
        assertThat(
            callable.call(),
            equalTo("after")
        )
    }

    object Register {
        private
        val local = ThreadLocal<Any?>()

        var value: Any?
            get() = local.get()
            set(value) = local.set(value)
    }

    data class BeanOf<T>(val value: T)
}
