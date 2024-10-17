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

package org.gradle.internal.declarativedsl.evaluator

import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.analysis.ResolutionError
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTraceElement
import org.gradle.internal.declarativedsl.evaluator.runner.EvaluationResult.NotEvaluated.StageFailure
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.evaluator.checks.DocumentCheckFailureReason
import org.gradle.internal.declarativedsl.language.ParsingError
import org.gradle.internal.declarativedsl.language.SingleFailureResult
import org.gradle.internal.declarativedsl.language.UnsupportedConstruct


object EvaluationFailureMessageGenerator {
    fun generateFailureMessage(
        scriptSourceIdentifier: String,
        stageFailures: List<StageFailure>
    ): String = buildString {
        appendLine("Failed to interpret the declarative DSL file '$scriptSourceIdentifier':")
        stageFailures.forEach { stageFailure ->
            when (stageFailure) {
                is StageFailure.FailuresInLanguageTree -> {
                    appendLine("Failures in building the language tree:".indent(1))
                    if (stageFailure.failures.isNotEmpty()) {
                        formatFailuresInLanguageTree(stageFailure.failures).forEach {
                            appendLine(it.indent(2))
                        }
                    }
                }

                is StageFailure.FailuresInResolution -> {
                    appendLine("Failures in resolution:".indent(1))
                    stageFailure.errors.forEach {
                        appendLine(formatResolutionError(it).indent(2))
                    }
                }

                StageFailure.NoParseResult -> appendLine("Failed to parse due to syntax errors")
                is StageFailure.NoSchemaAvailable -> appendLine("No associated schema for ${stageFailure.scriptContext}")
                is StageFailure.AssignmentErrors -> {
                    appendLine("Failures in assignments:".indent(1))
                    stageFailure.usages.forEach { unassigned ->
                        appendLine(describedUnassignedValueUsage(unassigned).indent(2))
                    }
                }

                is StageFailure.DocumentCheckFailures -> {
                    appendLine("Failures in document checks:".indent(1))
                    stageFailure.failures.forEach { failure ->
                        appendLine(describeDocumentCheckFailure(failure).indent(2))
                    }
                }
            }
        }
    }

    private
    fun formatFailuresInLanguageTree(failures: List<SingleFailureResult>): List<String> = buildList {
        failures.forEach { failure ->
            when (failure) {
                is UnsupportedConstruct -> add(formatUnsupportedConstruct(failure))
                is ParsingError -> add(formatParsingError(failure))
            }
        }
    }

    private
    fun formatUnsupportedConstruct(unsupportedConstruct: UnsupportedConstruct) =
        // TODO: use a proper phrase instead of the feature enum value name
        "${locationPrefixString(unsupportedConstruct.erroneousSource)}: unsupported language feature: ${unsupportedConstruct.languageFeature}"

    private
    fun formatParsingError(parsingError: ParsingError) =
        "${locationPrefixString(parsingError.erroneousSource)}: parsing error: ${parsingError.message}"


    private
    fun formatResolutionError(resolutionError: ResolutionError): String =
        elementLocationString(resolutionError.element) + ": " + describeResolutionErrorReason(resolutionError.errorReason)

    private
    fun describeResolutionErrorReason(errorReason: ErrorReason) = when (errorReason) {
        is ErrorReason.AmbiguousFunctions ->
            "ambiguous functions: " +
                errorReason.functions.joinToString(",") { resolution ->
                    resolution.schemaFunction.simpleName + "(" + resolution.binding.binding.keys.joinToString { it.name.plus(": ") + it.type } + ")"
                }

        is ErrorReason.AmbiguousImport -> "ambiguous import '${errorReason.fqName}'"
        is ErrorReason.AssignmentTypeMismatch -> "assignment type mismatch, expected '${errorReason.expected}', got '${errorReason.actual}'"
        ErrorReason.DanglingPureExpression -> "dangling pure expression"
        is ErrorReason.DuplicateLocalValue -> "duplicate local 'val ${errorReason.name}'"
        is ErrorReason.ExternalReassignment -> "assignment to external property"
        ErrorReason.MissingConfigureLambda -> "a configuring block expected but not found"
        is ErrorReason.ReadOnlyPropertyAssignment -> "assignment to read-only property '${errorReason.property.name}"
        ErrorReason.UnitAssignment -> "assignment of a Unit value"
        ErrorReason.UnresolvedAssignmentLhs -> "unresolved assignment target"
        ErrorReason.UnresolvedAssignmentRhs -> "unresolved assigned value"
        is ErrorReason.UnresolvedReference -> "unresolved reference '${errorReason.reference.sourceData.text()}'"
        ErrorReason.UnusedConfigureLambda -> "a configuring block is not expected"
        is ErrorReason.ValReassignment -> "assignment to a local 'val ${errorReason.localVal.name}'"
        is ErrorReason.UnresolvedFunctionCallArguments -> "unresolved function call arguments for '${errorReason.functionCall.name}'"
        is ErrorReason.UnresolvedFunctionCallReceiver -> "unresolved function call receiver for '${errorReason.functionCall.name}'"
        is ErrorReason.UnresolvedFunctionCallSignature -> "unresolved function call signature for '${errorReason.functionCall.name}'"
        ErrorReason.AccessOnCurrentReceiverOnlyViolation -> "this member can only be accessed on a current receiver"
        is ErrorReason.NonReadableProperty -> "property cannot be used as a value: '${errorReason.property.name}'"
        is ErrorReason.OpaqueArgumentForIdentityParameter -> "opaque identity argument: ${errorReason.parameter.name} = ${errorReason.argument.originElement.sourceData.text()}"
    }

    private
    fun describedUnassignedValueUsage(unassigned: AssignmentTraceElement.FailedToRecordAssignment): String {
        val errorMessage = when (unassigned) {
            is AssignmentTraceElement.Reassignment -> "Value reassigned"
            is AssignmentTraceElement.UnassignedValueUsed -> "Unassigned value used"
        }
        return "${elementLocationString(unassigned.lhs.receiverObject.originElement)}: $errorMessage in ${unassigned.lhs} := ${unassigned.rhs}"
    }

    private
    fun elementLocationString(languageTreeElement: LanguageTreeElement): String =
        locationPrefixString(languageTreeElement.sourceData)

    private
    fun describeDocumentCheckFailure(failure: DocumentCheckFailure): String =
        locationPrefixString(failure.location.sourceData) + ": " + describeDocumentCheckFailureReason(failure.reason)

    private
    fun describeDocumentCheckFailureReason(reason: DocumentCheckFailureReason) = when (reason) {
        DocumentCheckFailureReason.PluginManagementBlockOrderViolated -> "illegal content before 'pluginManagement', which can only appear as the first element in the file"
        DocumentCheckFailureReason.PluginsBlockOrderViolated -> "illegal content before 'plugins', which can only be preceded by 'pluginManagement'"
        DocumentCheckFailureReason.DuplicatePluginManagementBlock -> "duplicate 'pluginManagement'"
        DocumentCheckFailureReason.DuplicatePluginsBlock -> "duplicate 'plugins'"
        is DocumentCheckFailureReason.UnsupportedSyntaxInDocument -> "unsupported syntax (${reason.cause})"
    }

    private
    fun locationPrefixString(ast: SourceData): String =
        if (ast.lineRange.first != -1) "${ast.lineRange.first}:${ast.startColumn}" else ""

    private
    fun String.indent(level: Int = 1) = " ".repeat(level * 2) + this
}
