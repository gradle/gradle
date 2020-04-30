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
import java.util.function.Supplier


class SupplierCodecTest : AbstractFunctionalTypeTest() {

    @Test
    fun `defers execution of Supplier objects`() {
        assertDeferredExecutionOf(Supplier { Register.value }) {
            get()
        }
    }

    @Test
    fun `defers execution of dynamic Supplier fields`() {
        assertDeferredExecutionOf(BeanOf(Supplier { Register.value })) {
            value.get()
        }
    }

    @Test
    fun `defers execution of static Supplier fields`() {
        assertDeferredExecutionOf(SupplierBean(Supplier { Register.value })) {
            value.get()
        }
    }

    data class SupplierBean(val value: Supplier<Any?>)
}
