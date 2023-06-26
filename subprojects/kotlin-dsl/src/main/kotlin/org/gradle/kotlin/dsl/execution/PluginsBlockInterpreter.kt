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

    val dot = wsOrNewLine() * token(DOT) * wsOrNewLine()

    val version = symbol("version")

    val apply = symbol("apply")

    val bool = token(TRUE_KEYWORD) { true } + token(FALSE_KEYWORD) { false }

    val parenBool = paren(bool)

    val dotVersion = dot * version * parenString * ws()

    val dotApply = dot * apply * parenBool * ws()

    val infixVersion = version * (parenString + stringLiteral()) * ws()

    val infixApply = apply * (parenBool + bool) * ws()

    val optionalApply = optional(dotApply + infixApply)

    val optionalVersion = optional(dotVersion + infixVersion)

    val infixVersionApply = infixVersion * optionalApply

    val infixApplyVersion = flip(infixApply, optionalVersion)

    val dotVersionApply = dotVersion * optionalApply

    val dotApplyVersion = flip(dotApply, optionalVersion)

    val optionalVersionAndApply = optional(infixVersionApply + infixApplyVersion + dotVersionApply + dotApplyVersion)

    val pluginIdSpec = zip(pluginId, optionalVersionAndApply) { id, versionAndApply ->
        when (versionAndApply) {
            null -> ResidualProgram.PluginRequestSpec(id)
            else -> versionAndApply.let { (v, a) ->
                ResidualProgram.PluginRequestSpec(id, version = v, apply = a ?: true)
            }
        }
    }

    val kotlinDslSpec = zip(symbol("`kotlin-dsl`"), optionalApply) { _, a ->
        ResidualProgram.PluginRequestSpec(
            "org.gradle.kotlin.kotlin-dsl",
            expectedKotlinDslPluginsVersion,
            apply = a ?: true
        )
    }

    val pluginSpec = pluginIdSpec + kotlinDslSpec

    token(LBRACE) * wsOrNewLine() *
        zeroOrMore(pluginSpec * statementSeparator()) *
        optional(pluginSpec * wsOrNewLine()) *
        token(RBRACE)
}
