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

package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.Element
import org.gradle.internal.declarativedsl.language.ElementResult
import org.gradle.internal.declarativedsl.language.ErroneousStatement
import org.gradle.internal.declarativedsl.language.FailingResult
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.Import
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.MultipleFailuresResult
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.NamedReference
import org.gradle.internal.declarativedsl.language.SingleFailureResult
import org.gradle.internal.declarativedsl.language.This


internal
fun collectFailures(results: Iterable<ElementResult<*>>): List<SingleFailureResult> = buildList {
    fun addExpanded(failingResult: FailingResult) {
        when (failingResult) {
            is SingleFailureResult -> add(failingResult)
            is MultipleFailuresResult -> failingResult.failures.forEach(::addExpanded)
        }
    }

    fun collectFrom(current: LanguageTreeElement) {
        when (current) {
            is ErroneousStatement -> addExpanded(current.failingResult)
            is Block -> current.content.forEach(::collectFrom)
            is Assignment -> {
                collectFrom(current.lhs)
                collectFrom(current.rhs)
            }

            is FunctionCall -> {
                current.receiver?.let(::collectFrom)
                current.args.forEach(::collectFrom)
            }

            is NamedReference -> current.receiver?.let(::collectFrom)
            is LocalValue -> collectFrom(current.rhs)
            is FunctionArgument.Lambda -> collectFrom(current.block)
            is FunctionArgument.ValueArgument -> collectFrom(current.expr)

            is Import,
            is Literal<*>,
            is Null,
            is This -> Unit
        }
    }

    results.forEach {
        when (it) {
            is Element -> collectFrom(it.element)
            is FailingResult -> addExpanded(it)
        }
    }
}
