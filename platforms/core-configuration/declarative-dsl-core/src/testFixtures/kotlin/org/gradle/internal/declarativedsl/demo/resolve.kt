/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.demo

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.Resolver
import org.gradle.internal.declarativedsl.analysis.tracingCodeResolver
import org.gradle.internal.declarativedsl.language.FailingResult
import org.gradle.internal.declarativedsl.language.MultipleFailuresResult
import org.gradle.internal.declarativedsl.language.ParsingError
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.language.UnsupportedConstruct
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse

fun AnalysisSchema.resolve(
    code: String,
    resolver: Resolver = tracingCodeResolver()
): ResolutionResult {
    val parsedTree = parse(code)

    val languageBuilder = DefaultLanguageTreeBuilder()
    val tree = languageBuilder.build(parsedTree, SourceIdentifier("demo"))

    val failures = tree.allFailures

    if (failures.isNotEmpty()) {
        println("Failures:")
        fun printFailures(failure: FailingResult) {
            when (failure) {
                is ParsingError -> println(
                    "Parsing error: " + failure.message
                )
                is UnsupportedConstruct -> println(
                    failure.languageFeature.toString() + " in " + parsedTree.wrappedCode.slice(parsedTree.originalCodeOffset..parsedTree.originalCodeOffset + 100)
                )
                is MultipleFailuresResult -> failure.failures.forEach { printFailures(it) }
            }
        }
        failures.forEach { printFailures(it) }
    }

    val result = resolver.resolve(this, tree.imports, tree.topLevelBlock)
    return result
}
