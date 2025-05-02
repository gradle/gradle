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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test
import kotlin.reflect.KClass


class LambdaTest {
    @Test
    fun `if a lambda is required, missing lambda is reported as an error`() {
        schema.resolve("lambdaRequired(0)").isError(ErrorReason.UnresolvedFunctionCallSignature::class)
    }

    @Test
    fun `if a lambda is required, a lambda is accepted`() {
        schema.resolve("lambdaRequired(0) { }").isSuccessful()
    }

    @Test
    fun `if a lambda is optional, a missing lambda is ok`() {
        schema.resolve("lambdaOptional(0)").isSuccessful()
    }

    @Test
    fun `if a lambda is optional, a lambda is accepted`() {
        schema.resolve("lambdaOptional(0) { }").isSuccessful()
    }

    @Test
    fun `if a lambda is not allowed, missing lambda is ok`() {
        schema.resolve("lambdaNotAllowed(0)").isSuccessful()
    }

    @Test
    fun `if a lambda is not allowed, a lambda is reported as an error`() {
        schema.resolve("lambdaNotAllowed(0) { }").isError(ErrorReason.UnresolvedFunctionCallSignature::class)
    }

    private
    fun ResolutionResult.isSuccessful() {
        assertTrue { errors.isEmpty() }
        assertTrue { additions.size == 1 }
    }

    private
    fun ResolutionResult.isError(errorReason: KClass<out ErrorReason>) {
        assertTrue { errors.any { errorReason.isInstance(it.errorReason) } }
    }

    private
    val schema = schemaFromTypes(Receiver::class, listOf(Receiver::class))

    @Suppress("unused")
    private
    abstract class Receiver {
        @Adding
        abstract fun lambdaRequired(x: Int, configure: Receiver.() -> Unit): Receiver

        @Adding
        abstract fun lambdaOptional(x: Int, configure: Receiver.() -> Unit = { }): Receiver

        @Adding
        abstract fun lambdaNotAllowed(x: Int): Receiver
    }
}
