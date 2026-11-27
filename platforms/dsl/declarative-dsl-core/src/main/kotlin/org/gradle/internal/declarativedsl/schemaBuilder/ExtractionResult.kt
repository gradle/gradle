/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.internal.declarativedsl.schemaBuilder.ExtractionResult.Extracted
import org.gradle.internal.declarativedsl.schemaBuilder.ExtractionResult.Failure

/**
 * Carries either a successfully [Extracted] part of a schema with a [T] value or a [Failure]; both also have a piece of metadata [M].
 *
 * @see SchemaResult for the cases when the metadata is not needed.
 */
sealed interface ExtractionResult<out T, out M> {
    val metadata: M

    data class Extracted<out T, out M>(val result: T, override val metadata: M) : ExtractionResult<T, M>
    data class Failure<out M>(val failure: SchemaResult.Failure, override val metadata: M) : ExtractionResult<Nothing, M>

    companion object {
        fun <T, M> of(extractionResult: SchemaResult<T>, metadata: M): ExtractionResult<T, M> = when (extractionResult) {
            is SchemaResult.Result -> Extracted(extractionResult.result, metadata)
            is SchemaResult.Failure -> Failure(extractionResult, metadata)
        }
    }
}

fun <T, M> Iterable<ExtractionResult<T, M>>.combineGroupsByResult(combineMetadata: (Iterable<M>) -> M): Iterable<ExtractionResult<T, M>> {
    fun <T, M, N> ExtractionResult<T, M>.replaceMetadata(mapMetadata: (ExtractionResult<T, M>) -> N): ExtractionResult<T, N> =
        when (this) {
            is Extracted -> Extracted(result, mapMetadata(this))
            is Failure -> Failure(failure, mapMetadata(this))
        }

    return groupBy { extractionResult ->
        extractionResult.replaceMetadata {
            when (it) {
                is Extracted -> emptyList() // Group all success results into one bin
                is Failure -> listOf(it) // Keep all failures separate – make the group key unique by using the item in it
            }
        }
    }.run {
        keys.map { resultGroupKey ->
            when (resultGroupKey) {
                is Extracted -> Extracted(resultGroupKey.result, combineMetadata(getValue(resultGroupKey).map { it.metadata }))
                is Failure -> resultGroupKey.metadata.single() // piggybacking on the metadata holding the entire Failure item
            }
        }
    }
}

inline fun <T, M> ExtractionResult<T, M>.orFailWith(onFailure: (Failure<M>) -> Nothing): T = when (this) {
    is Extracted -> result
    is Failure -> onFailure(this)
}

inline fun <T, M, R> ExtractionResult<T, M>.map(transformResult: (T) -> R): ExtractionResult<R, M> = when (this) {
    is Extracted -> Extracted(transformResult(result), metadata)
    is Failure -> this
}

inline fun <T, M, R> ExtractionResult<T, M>.flatMap(transform: (T) -> Extracted<R, M>): ExtractionResult<R, M> = when (this) {
    is Extracted -> transform(result)
    is Failure -> this
}
