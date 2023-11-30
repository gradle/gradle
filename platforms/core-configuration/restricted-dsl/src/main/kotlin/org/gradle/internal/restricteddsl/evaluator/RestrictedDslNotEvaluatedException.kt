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

package org.gradle.internal.restricteddsl.evaluator

import com.h0tk3y.kotlin.staticObjectNotation.analysis.ErrorReason
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionError
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.FailingResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.MultipleFailuresResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedConstruct
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.text
import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.AssignmentTraceElement
import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.astInfoOrNull
import org.gradle.api.GradleException
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.restricteddsl.evaluator.RestrictedKotlinScriptEvaluator.EvaluationResult.NotEvaluated.NotEvaluatedReason


internal
class RestrictedDslNotEvaluatedException(
    private val scriptSource: ScriptSource,
    private val notEvaluatedReason: NotEvaluatedReason
) : GradleException() {
    override val message: String
        get() = buildString {
            append("Restricted DSL file '${scriptSource.fileName}' ")
            when (notEvaluatedReason) {
                is NotEvaluatedReason.FailuresInLanguageTree -> {
                    append("has failures in building the language tree")
                    if (notEvaluatedReason.failures.isNotEmpty()) {
                        appendLine()
                        formatFailuresInLanguageTree(notEvaluatedReason.failures).forEach {
                            appendLine(it.indent())
                        }
                    }
                }

                is NotEvaluatedReason.FailuresInResolution -> {
                    appendLine("has failures in resolution")
                    notEvaluatedReason.errors.forEach {
                        appendLine(formatResolutionError(it).intern())
                    }
                }

                NotEvaluatedReason.NoParseResult -> append("contains syntax errors")
                is NotEvaluatedReason.NoSchemaAvailable -> append("has no associated schema for ${notEvaluatedReason.target}")
                is NotEvaluatedReason.UnassignedValuesUsed -> {
                    appendLine("used unassigned values")
                    notEvaluatedReason.usages.forEach { unassigned ->
                        appendLine(describedUnassignedValueUsage(unassigned))
                    }
                }
            }
        }

    private
    fun formatFailuresInLanguageTree(failures: List<FailingResult>): List<String> = buildList {
        fun failure(failingResult: FailingResult) {
            when (failingResult) {
                is MultipleFailuresResult -> failingResult.failures.forEach(::failure)
                is UnsupportedConstruct -> add(formatUnsupportedConstruct(failingResult))
            }
        }
        failures.forEach { failure(it) }
    }

    private
    fun formatUnsupportedConstruct(unsupportedConstruct: UnsupportedConstruct) =
        // TODO: use a proper phrase instead of the feature enum value name
        "${astLocationPrefixString(unsupportedConstruct.erroneousAst)}: unsupported language feature: ${unsupportedConstruct.languageFeature}"

    private
    fun formatResolutionError(resolutionError: ResolutionError): String =
        elementLocationString(resolutionError.element).indent() + ": " + describeResolutionErrorReason(resolutionError.errorReason)

    private
    fun describeResolutionErrorReason(errorReason: ErrorReason) = when (errorReason) {
        is ErrorReason.AmbiguousFunctions ->
            "ambiguous functions: " +
                errorReason.functions.joinToString(",") { resolution ->
                    resolution.schemaFunction.simpleName + "(" + resolution.binding.binding.keys.joinToString { it.name?.plus(": ").orEmpty() + it.type } + ")"
                }

        is ErrorReason.AmbiguousImport -> "ambiguous import '${errorReason.fqName}'"
        is ErrorReason.AssignmentTypeMismatch -> "assignment type mismatch, expected '${errorReason.expected}', got '${errorReason.actual}'"
        ErrorReason.DanglingPureExpression -> "dangling pure expression"
        is ErrorReason.DuplicateLocalValue -> "duplicate local 'val ${errorReason.name}'"
        is ErrorReason.ExternalReassignment -> "assignment to external property"
        ErrorReason.MissingConfigureLambda -> "a configuring block expected but not found"
        ErrorReason.ReadOnlyPropertyAssignment -> "read-only property assignment"
        ErrorReason.UnitAssignment -> "assignment of a Unit value"
        ErrorReason.UnresolvedAssignmentLhs -> "unresolved assignment target"
        ErrorReason.UnresolvedAssignmentRhs -> "unresolved assigned value"
        is ErrorReason.UnresolvedReference -> "unresolved reference '${errorReason.reference.originAst.text}'"
        ErrorReason.UnusedConfigureLambda -> "a configuring block is not expected"
        is ErrorReason.ValReassignment -> "assignment to a local 'val ${errorReason.localVal.name}'"
        is ErrorReason.UnresolvedFunctionCallArguments -> "unresolved function call arguments for '${errorReason.functionCall.name}'"
        is ErrorReason.UnresolvedFunctionCallReceiver -> "unresolved function call receiver for '${errorReason.functionCall.name}'"
        is ErrorReason.UnresolvedFunctionCallSignature -> "unresolved function call signature for '${errorReason.functionCall.name}'"
    }

    private
    fun describedUnassignedValueUsage(unassigned: AssignmentTraceElement.UnassignedValueUsed) =
        "${elementLocationString(unassigned.lhs.receiverObject.originElement)}: ${unassigned.lhs} := (unassigned) ${unassigned.rhs}"

    private
    fun elementLocationString(languageTreeElement: LanguageTreeElement): String =
        astLocationPrefixString(languageTreeElement.originAst)

    private
    fun astLocationPrefixString(ast: Ast): String =
        ast.astInfoOrNull?.start?.toString().orEmpty()

    private
    fun String.indent(level: Int = 1) = " ".repeat(level * 2) + this
}
