/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.configuration.problems

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.configuration.problems.StructuredMessage.Fragment.Reference
import org.gradle.internal.configuration.problems.StructuredMessage.Fragment.Text
import org.gradle.problems.internal.report.model.JsBuildLogic
import org.gradle.problems.internal.report.model.JsBuildLogicClass
import org.gradle.problems.internal.report.model.JsDiagnostic
import org.gradle.problems.internal.report.model.JsError
import org.gradle.problems.internal.report.model.JsMessageFragment
import org.gradle.problems.internal.report.model.JsModel
import org.gradle.problems.internal.report.model.JsStackTracePart
import org.gradle.problems.internal.report.model.JsTrace
import org.gradle.problems.internal.report.model.JsTraceBean
import org.gradle.problems.internal.report.model.JsTraceCapturedArguments
import org.gradle.problems.internal.report.model.JsTraceField
import org.gradle.problems.internal.report.model.JsTraceGradle
import org.gradle.problems.internal.report.model.JsTraceInputProperty
import org.gradle.problems.internal.report.model.JsTraceOutputProperty
import org.gradle.problems.internal.report.model.JsTraceProject
import org.gradle.problems.internal.report.model.JsTracePropertyUsage
import org.gradle.problems.internal.report.model.JsTraceSerializedLambda
import org.gradle.problems.internal.report.model.JsTraceSystemProperty
import org.gradle.problems.internal.report.model.JsTraceTask
import org.gradle.problems.internal.report.model.JsTraceUnknown
import org.gradle.problems.internal.report.model.JsTraceVirtualProperty


/**
 * Maps the internal report types onto the wire model shared with the report renderer.
 */


/** The top-level configuration cache report envelope, without its diagnostics (streamed separately). */
fun ProblemReportDetails.toJsModel(): JsModel = JsModel(
    buildName = buildDisplayName,
    cacheAction = cacheAction,
    requestedTasks = requestedTasks,
    cacheActionDescription = cacheActionDescription.toJsMessage(),
    documentationLink = DocumentationRegistry().getDocumentationFor("configuration_cache"),
    totalProblemCount = totalProblemCount,
    uniqueProblemCount = uniqueProblemCount,
    overflownProblemCount = overflownProblemCount,
)


/** A single configuration cache report diagnostic (input, problem or incompatible task). */
fun DecoratedReportProblem.toJsDiagnostic(): JsDiagnostic {
    val message = message.toJsMessage()
    return JsDiagnostic(
        input = message.takeIf { kind == DiagnosticKind.INPUT },
        problem = message.takeIf { kind == DiagnosticKind.PROBLEM },
        incompatibleTask = message.takeIf { kind == DiagnosticKind.INCOMPATIBLE_TASK },
        trace = trace.toJsTrace(),
        documentationLink = docLink,
        error = failure?.toJsError(),
    )
}


private fun StructuredMessage.toJsMessage(): List<JsMessageFragment> =
    fragments.map { fragment ->
        when (fragment) {
            is Text -> JsMessageFragment(text = fragment.text)
            is Reference -> JsMessageFragment(name = fragment.name)
        }
    }


fun DecoratedFailure.toJsError(): JsError = JsError(
    summary = summary?.toJsMessage(),
    parts = parts?.map { part ->
        if (part.isInternal) JsStackTracePart(internalText = part.text)
        else JsStackTracePart(text = part.text)
    },
)


/** The whole property trace chain, from the reported value up to the build graph root. */
fun PropertyTrace.toJsTrace(): List<JsTrace> =
    sequence.map { it.toJsTraceElement() }.toList()


private fun PropertyTrace.toJsTraceElement(): JsTrace = when (this) {
    is PropertyTrace.Property -> when (kind) {
        PropertyKind.Field -> JsTraceField(name = name, declaringType = firstTypeFrom(trace).name)
        PropertyKind.PropertyUsage -> JsTracePropertyUsage(name = name, from = projectPathFrom(trace))
        PropertyKind.InputProperty -> JsTraceInputProperty(name = name, task = taskPathFrom(trace))
        PropertyKind.OutputProperty -> JsTraceOutputProperty(name = name, task = taskPathFrom(trace))
    }

    is PropertyTrace.CapturedLambdaArguments -> JsTraceCapturedArguments(
        `class` = owningClass,
        method = owningMethod,
        subkind = when (subkind) {
            PropertyTrace.CapturedLambdaArguments.Subkind.LambdaBody -> "lambdaBody"
            PropertyTrace.CapturedLambdaArguments.Subkind.BoundReceiver -> "boundReceiver"
        },
    )

    is PropertyTrace.VirtualProperty -> JsTraceVirtualProperty(
        name = name,
        owner = when (val owner = owner) {
            is PropertyTrace.Task -> taskPathFrom(owner)
            else -> firstTypeFrom(owner).name
        },
    )

    is PropertyTrace.SystemProperty -> JsTraceSystemProperty(name = name)

    is PropertyTrace.Task -> JsTraceTask(path = path, type = type.name)

    is PropertyTrace.Bean -> JsTraceBean(type = type.name)

    is PropertyTrace.SerializedLambda -> JsTraceSerializedLambda(type = functionalInterfaceClass, returns = instantiatedReturnType)

    is PropertyTrace.Project -> JsTraceProject(path = path)

    is PropertyTrace.BuildLogic -> JsBuildLogic(location = source.displayName)

    is PropertyTrace.BuildLogicClass -> JsBuildLogicClass(type = name)

    PropertyTrace.Gradle -> JsTraceGradle

    PropertyTrace.Unknown -> JsTraceUnknown
}
