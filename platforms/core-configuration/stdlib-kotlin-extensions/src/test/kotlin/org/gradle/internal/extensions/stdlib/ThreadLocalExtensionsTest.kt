/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.extensions.stdlib

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadLocalExtensionsTest {
    @Test
    fun `thread local value is non-nullable by default`() {
        val tl by threadLocal { false }

        // We want but cannot assert that tl = null is a compilation error.
        assertFalse(tl)
    }

    @Test
    fun `nullable thread local value can be initialized with null`() {
        val tl by threadLocal<Boolean?> { null }

        assertNull(tl)
    }

    @Test
    fun `nullable thread local variable can be initialized with nullable supplier`() {
        var tl by threadLocal(::nullableSupplier)

        assertFalse(tl!!)

        tl = null
        assertNull(tl)
    }

    @Test
    fun `nullable thread local variable can be set to null`() {
        var tl by threadLocal<Boolean?> { false }

        tl = null
        assertNull(tl)
    }

    @Suppress("RedundantNullableReturnType", "FunctionOnlyReturningConstant")
    private
    fun nullableSupplier(): Boolean? {
        return false
    }
}
