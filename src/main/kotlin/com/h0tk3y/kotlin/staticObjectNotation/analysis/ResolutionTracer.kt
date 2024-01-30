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

package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionTrace.ResolutionOrErrors.Errors
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionTrace.ResolutionOrErrors.NoResolution
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionTrace.ResolutionOrErrors.Resolution
import com.h0tk3y.kotlin.staticObjectNotation.language.Assignment
import com.h0tk3y.kotlin.staticObjectNotation.language.Expr
import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement
import com.h0tk3y.kotlin.staticObjectNotation.language.LocalValue
import java.util.*

interface ResolutionTrace {
    sealed interface ResolutionOrErrors<out R> {
        data class Resolution<R>(val result: R) : ResolutionOrErrors<R>
        data class Errors(val errors: List<ResolutionError>) : ResolutionOrErrors<Nothing>
        data object NoResolution : ResolutionOrErrors<Nothing>
    }

    fun assignmentResolution(assignment: Assignment): ResolutionOrErrors<AssignmentRecord>
    fun expressionResolution(expr: Expr): ResolutionOrErrors<ObjectOrigin>
}

internal class ResolutionTracer(
    private val expressionResolver: ExpressionResolver,
    private val statementResolver: StatementResolver,
    private val errorCollector: ErrorCollector
) : ExpressionResolver, StatementResolver, ErrorCollector, ResolutionTrace {

    private val assignmentResolutions = IdentityHashMap<Assignment, AssignmentRecord>()
    private val expressionResolution = IdentityHashMap<Expr, ObjectOrigin>()
    private val elementErrors = IdentityHashMap<LanguageTreeElement, MutableList<ResolutionError>>()

    override fun assignmentResolution(assignment: Assignment): ResolutionTrace.ResolutionOrErrors<AssignmentRecord> =
        assignmentResolutions[assignment]?.let { resolution ->
            check(assignment !in elementErrors)
            Resolution(resolution)
        } ?: elementErrors[assignment]?.let {errors ->
            Errors(errors)
        } ?: NoResolution

    override fun expressionResolution(expr: Expr): ResolutionTrace.ResolutionOrErrors<ObjectOrigin> =
        expressionResolution[expr]?.let { resolution ->
            check(expr !in elementErrors)
            Resolution(resolution)
        } ?: elementErrors[expr]?.let { errors ->
            Errors(errors)
        } ?: NoResolution

    override fun doResolveExpression(context: AnalysisContext, expr: Expr): ObjectOrigin? {
        val result = expressionResolver.doResolveExpression(context, expr)
        if (result != null) {
            expressionResolution[expr] = result
        }
        return result
    }

    override fun doResolveAssignment(context: AnalysisContext, assignment: Assignment): AssignmentRecord? {
        val result = statementResolver.doResolveAssignment(context, assignment)
        if (result != null) {
            assignmentResolutions[assignment] = result
        }
        return result
    }

    override fun doResolveLocalValue(context: AnalysisContext, localValue: LocalValue) =
        statementResolver.doResolveLocalValue(context, localValue)

    override fun doResolveExpressionStatement(context: AnalysisContext, expr: Expr) =
        statementResolver.doResolveExpressionStatement(context, expr)

    override fun collect(error: ResolutionError) {
        elementErrors.getOrPut(error.element) { mutableListOf() }.add(error)
        errorCollector.collect(error)
    }

    override val errors: List<ResolutionError>
        get() = errorCollector.errors
}
