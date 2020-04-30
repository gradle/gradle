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

import org.junit.Test


class FunctionCodecTest : AbstractFunctionalTypeTest() {

    @Test
    fun `defers execution of Function objects`() {
        assertDeferredExecutionOf(function()) {
            invoke()
        }
    }

    @Test
    fun `defers execution of dynamic Function fields`() {
        assertDeferredExecutionOf(BeanOf(function())) {
            value()
        }
    }

    @Test
    fun `defers execution of static Function fields`() {
        assertDeferredExecutionOf(FunctionBean(function())) {
            value()
        }
    }

    private
    fun function(): () -> Any? = { Runtime.value }

    data class FunctionBean(val value: () -> Any?)
}
