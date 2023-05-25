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


@Suppress("unused")
private
val debugger: ParserDebugger = ParserDebugger()


@Suppress("UNUSED_PARAMETER")
private
fun <T> debug(name: String, parser: Parser<T>) =
//    debugger.debug(name, parser)
    parser


private
val combinator = Combinator(ignoresComments = true, ignoresNewline = false)


private
val pluginsBlockParser = run {

    combinator.run {

        val parenString = debug("parenString",
            paren(stringLiteral)
        )

        val parenBool = debug("parenBool",
            paren(booleanLiteral)
        )
        val kotlinPluginId = debug("kotlinPluginId",
            symbol("kotlin") * parenString.map { "org.jetbrains.kotlin.$it" }
        )

        val pluginId = debug("pluginId",
            (symbol("id") * parenString + kotlinPluginId)
        )

        val dot = debug("dot",
            wsOrNewLine() * token(DOT)
        )

        val version = debug("version",
            symbol("version")
        )

        val apply = debug("apply",
            symbol("apply")
        )


        val dotVersion = debug("dotVersion",
            dot * version * parenString
        )

        val dotApply = debug("dotApply",
            dot * apply * parenBool
        )

        val infixVersion = debug("infixVersion",
            version * (parenString + stringLiteral)
        )

        val infixApply = debug("infixApply",
            apply * (parenBool + booleanLiteral)
        )

        val optionalApply = debug("optionalApply",
            optional(dotApply + infixApply)
        )

        val optionalVersion = debug("optionalVersion",
            optional(dotVersion + infixVersion)
        )

        val infixVersionApply = debug("infixVersionApply",
            infixVersion * optionalApply
        )

        val infixApplyVersion = debug("infixApplyVersion",
            flip(infixApply, optionalVersion)
        )

        val dotVersionApply = debug("dotVersionApply",
            dotVersion * optionalApply
        )

        val dotApplyVersion = debug("dotApplyVersion",
            flip(dotApply, optionalVersion)
        )

        val optionalVersionAndApply = debug("optionalVersionAndApply",
            optional(infixVersionApply + infixApplyVersion + dotVersionApply + dotApplyVersion)
        )

        val pluginIdSpec = debug("pluginIdSpec",
            zip(pluginId, optionalVersionAndApply) { id, versionAndApply ->
                when (versionAndApply) {
                    null -> ResidualProgram.PluginRequestSpec(id)
                    else -> versionAndApply.let { (v, a) ->
                        ResidualProgram.PluginRequestSpec(id, version = v, apply = a ?: true)
                    }
                }
            }
        )

        val kotlinDslSpec = debug("kotlinDslSpec",
            zip(symbol("`kotlin-dsl`"), optionalApply) { _, a ->
                ResidualProgram.PluginRequestSpec(
                    "org.gradle.kotlin.kotlin-dsl",
                    expectedKotlinDslPluginsVersion,
                    apply = a ?: true
                )
            }
        )

        val pluginSpec = debug("pluginSpec",
            pluginIdSpec + kotlinDslSpec
        )

        val firstLines = debug("firstLines",
            zeroOrMore(pluginSpec * debug("statementSeparator", statementSeparator()))
        )

        val lastLine = debug("lastLine",
            optional(pluginSpec * wsOrNewLine())
        )

        token(LBRACE) * wsOrNewLine() *
            firstLines *
            lastLine *
            token(RBRACE)
    }
}
