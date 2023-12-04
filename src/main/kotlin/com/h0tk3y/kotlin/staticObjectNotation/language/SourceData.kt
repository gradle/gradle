/*
 * Copyright 2023 the original author or authors.
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

package com.h0tk3y.kotlin.staticObjectNotation.language

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.text
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.astInfoOrNull

interface SourceData {
    val sourceIdentifier: SourceIdentifier
    val indexRange: IntRange
    val lineRange: IntRange
    val startColumn: Int
    val endColumn: Int

    fun text(): String
}

interface SourceIdentifier {
    val fileIdentifier: String
}

class AstSourceIdentifier(
    val rootAst: Ast,
    override val fileIdentifier: String
) : SourceIdentifier

class AstSourceData(
    override val sourceIdentifier: SourceIdentifier,
    val currentAst: Ast
) : SourceData {
    override val indexRange: IntRange
        get() = currentAst.astInfoOrNull?.let { it.start.index..it.stop.index } ?: -1..-1
    override val lineRange: IntRange
        get() = currentAst.astInfoOrNull?.let { it.start.line..it.stop.line } ?: -1..-1
    override val startColumn: Int
        get() = currentAst.astInfoOrNull?.start?.row ?: -1
    override val endColumn: Int
        get() = currentAst.astInfoOrNull?.stop?.row ?: -1

    override fun text(): String = currentAst.text
}
