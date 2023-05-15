/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.kotlin.dsl.execution

import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.lexer.KtTokens.FALSE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.RBRACE
import org.jetbrains.kotlin.lexer.KtTokens.TRUE_KEYWORD


internal
sealed class PluginsBlockInterpretation {

    /**
     * The `plugins` block applies exactly the given list of [plugins].
     */
    data class Static(val plugins: List<ResidualProgram.PluginRequestSpec>) : PluginsBlockInterpretation()

    /**
     * The `plugins` block cannot be interpreted because of [reason].
     */
    data class Dynamic(val reason: String) : PluginsBlockInterpretation()
}


internal
fun interpret(program: Program.Plugins): PluginsBlockInterpretation {
    val blockString = program.fragment.blockString
    return when (val r = pluginsBlockParser(blockString)) {
        is ParserResult.Failure -> PluginsBlockInterpretation.Dynamic(r.reason)
        is ParserResult.Success -> PluginsBlockInterpretation.Static(
            r.result.let { (specs, spec) ->
                buildList(specs.size + (if (spec != null) 1 else 0)) {
                    addAll(specs)
                    spec?.let { add(it) }
                }
            }
        )
    }
}


private
val pluginsBlockParser = run {

    val parenString = paren(stringLiteral())

    val kotlinPluginId = symbol("kotlin") * parenString.map { "org.jetbrains.kotlin.$it" }

    val pluginId = (symbol("id") * parenString + kotlinPluginId) * ws()

    val dot = token(DOT)

    val version = symbol("version")

    val dotVersion = dot * ws() * version * parenString

    val infixVersion = version * ws() * (parenString + stringLiteral())

    val apply = symbol("apply")

    val bool = token(TRUE_KEYWORD) { true } + token(FALSE_KEYWORD) { false }

    val parenBool = paren(bool)

    val dotApply = dot * ws() * apply * parenBool

    val infixApply = apply * ws() * (parenBool + bool)

    val infixVersionApply = infixVersion * optional(ws() * infixApply)

    val dotVersionApply = dotVersion * optional(ws() * dotApply)

    val infixApplyVersion = flip(infixApply, optional(ws() * infixVersion))

    val dotApplyVersion = flip(dotApply, optional(ws() * dotVersion))

    val optionalVersionAndApply = optional(infixVersionApply + dotVersionApply + infixApplyVersion + dotApplyVersion)

    val pluginIdSpec = zip(pluginId, optionalVersionAndApply) { id, versionAndApply ->
        when (versionAndApply) {
            null -> ResidualProgram.PluginRequestSpec(id)
            else -> versionAndApply.let { (v, a) ->
                ResidualProgram.PluginRequestSpec(id, version = v, apply = a ?: true)
            }
        }
    }

    val kotlinDslSpec = symbol("`kotlin-dsl`").map {
        ResidualProgram.PluginRequestSpec("org.gradle.kotlin.kotlin-dsl", expectedKotlinDslPluginsVersion)
    }

    val pluginSpec = pluginIdSpec + kotlinDslSpec

    token(LBRACE) * wsOrNewLine() *
        many(pluginSpec * statementSeparator()) *
        optional(pluginSpec * wsOrNewLine()) *
        token(RBRACE)
}
