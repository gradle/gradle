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

import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.RBRACE


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

    val parenString = parens(stringLiteral())

    val pluginId = symbol("id") * parenString

    val dot = token(DOT)

    val versionParser: Parser<String> = run {
        val version = symbol("version")
        val versionMethod = dot * ws() * version * parenString
        val versionOperator = version * (parenString + (ws() * stringLiteral()))
        versionMethod + versionOperator
    }

    val pluginSpec = (pluginId * optional(ws() * versionParser)).map { (id, v) ->
        ResidualProgram.PluginRequestSpec(id, version = v)
    }

    token(LBRACE) * wsOrNewLine() *
        many(pluginSpec * statementSeparator()) *
        optional(pluginSpec * wsOrNewLine()) *
        token(RBRACE)
}
