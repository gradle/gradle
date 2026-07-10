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

package org.gradle.internal.declarativedsl

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.fail
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <reified T> assertIs(instance: Any?): T {
    contract { returns() implies (instance is T) }
    assertInstanceOf(T::class.java, instance)
    return instance as T
}

inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T =
    runCatching(block).fold(
        onSuccess = {
            fail("Excepted exception thrown (${T::class.java}) but succeeded")
        },
        onFailure = { exception ->
            assertIs<T>(exception)
        }
    )
