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

import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.restricteddsl.plugins.MutablePluginDependencySpec
import org.gradle.internal.restricteddsl.plugins.RuntimeTopLevelPluginsReceiver
import org.gradle.internal.restricteddsl.provider.RestrictedKotlinScriptEvaluator
import org.gradle.internal.restricteddsl.provider.defaultRestrictedKotlinScriptEvaluator
import org.gradle.kotlin.dsl.*
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
fun interpret(program: Program.Plugins, isRestrictedDslOnly: Boolean = false): PluginsBlockInterpretation {
    val pluginsTopLevelReceiver = RuntimeTopLevelPluginsReceiver()
    val isEvaluated = defaultRestrictedKotlinScriptEvaluator.evaluate(
        pluginsTopLevelReceiver,
        TextResourceScriptSource(StringTextResource("plugins block", program.fragment.identifierString))
    )
    if (isEvaluated is RestrictedKotlinScriptEvaluator.EvaluationResult.Evaluated) {
        return PluginsBlockInterpretation.Static(pluginsTopLevelReceiver.plugins.specs.map { it.toRequestSpec() })
    }
    if (isRestrictedDslOnly && isEvaluated is RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated) {
        return PluginsBlockInterpretation.Dynamic(isEvaluated.reason.toString())
    }

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
fun MutablePluginDependencySpec.toRequestSpec(): ResidualProgram.PluginRequestSpec =
    ResidualProgram.PluginRequestSpec(id, version, apply)


private
val combinator = Combinator(ignoresComments = true, ignoresNewline = false)


private
val pluginsBlockParser = run {

    combinator.run {

        val parenString by debug {
            paren(stringLiteral)
        }

        val parenBool by debug {
            paren(booleanLiteral)
        }

        val kotlinPluginId by debug {
            symbol("kotlin") * parenString.map { "org.jetbrains.kotlin.$it" }
        }

        val pluginId by debug {
            (symbol("id") * parenString + kotlinPluginId)
        }

        val dot by debug {
            wsOrNewLine() * token(DOT)
        }

        val version by debug {
            symbol("version")
        }

        val apply by debug {
            symbol("apply")
        }

        val dotVersion by debug {
            dot * version * parenString
        }

        val dotApply by debug {
            dot * apply * parenBool
        }

        val infixVersion by debug {
            version * (parenString + stringLiteral)
        }

        val infixApply by debug {
            apply * (parenBool + booleanLiteral)
        }

        val optionalApply by debug {
            optional(dotApply + infixApply)
        }

        val optionalVersion by debug {
            optional(dotVersion + infixVersion)
        }

        val infixVersionApply by debug {
            infixVersion * optionalApply
        }

        val infixApplyVersion by debug {
            flip(infixApply, optionalVersion)
        }

        val dotVersionApply by debug {
            dotVersion * optionalApply
        }

        val dotApplyVersion by debug {
            flip(dotApply, optionalVersion)
        }

        val optionalVersionAndApply by debug {
            optional(infixVersionApply + infixApplyVersion + dotVersionApply + dotApplyVersion)
        }

        val pluginIdSpec by debug {
            zip(pluginId, optionalVersionAndApply) { id, versionAndApply ->
                when (versionAndApply) {
                    null -> ResidualProgram.PluginRequestSpec(id)
                    else -> versionAndApply.let { (v, a) ->
                        ResidualProgram.PluginRequestSpec(id, version = v, apply = a ?: true)
                    }
                }
            }
        }

        val kotlinDslSpec by debug {
            zip(symbol("`kotlin-dsl`"), optionalApply) { _, a ->
                ResidualProgram.PluginRequestSpec(
                    "org.gradle.kotlin.kotlin-dsl",
                    expectedKotlinDslPluginsVersion,
                    apply = a ?: true
                )
            }
        }

        val pluginSpec by debug {
            pluginIdSpec + kotlinDslSpec
        }

        val firstLines by debug {
            zeroOrMore(pluginSpec * statementSeparator())
        }

        val lastLine by debug {
            optional(pluginSpec * wsOrNewLine())
        }

        token(LBRACE) * wsOrNewLine() *
            firstLines *
            lastLine *
            token(RBRACE)
    }
}
