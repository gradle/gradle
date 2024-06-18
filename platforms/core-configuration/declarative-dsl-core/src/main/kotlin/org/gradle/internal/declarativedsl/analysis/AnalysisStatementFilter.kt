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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall


object AnalysisStatementFilterUtils {
    val isConfiguringCall: AnalysisStatementFilter = AnalysisStatementFiltersImplementations.DefaultConfiguringCallFilter()

    val isTopLevelElement: AnalysisStatementFilter = AnalysisStatementFiltersImplementations.DefaultTopLevelElementFilter()

    fun isCallNamed(name: String): AnalysisStatementFilter = AnalysisStatementFiltersImplementations.DefaultNamedCallFilter(name)
}


val analyzeEverything: AnalysisStatementFilter = AnalysisStatementFiltersImplementations.DefaultAnalyzeEverythingFilter()


fun AnalysisStatementFilter.and(other: AnalysisStatementFilter): AnalysisStatementFilter = AnalysisStatementFiltersImplementations.DefaultAndFilter(this, other)


fun AnalysisStatementFilter.or(other: AnalysisStatementFilter): AnalysisStatementFilter = AnalysisStatementFiltersImplementations.DefaultOrFilter(this, other)


fun AnalysisStatementFilter.implies(other: AnalysisStatementFilter) = this.not().or(other)


fun AnalysisStatementFilter.not(): AnalysisStatementFilter = AnalysisStatementFiltersImplementations.DefaultNotFilter(this)


internal
fun AnalysisStatementFilter.shouldAnalyzeStatement(statement: DataStatement, scopes: List<AnalysisScopeView>): Boolean = when (this) {
    is AnalysisStatementFilter.AnalyzeEverythingFilter -> true
    is AnalysisStatementFilter.CompositionFilter.AndFilter -> left.shouldAnalyzeStatement(statement, scopes) && right.shouldAnalyzeStatement(statement, scopes)
    is AnalysisStatementFilter.CompositionFilter.OrFilter -> left.shouldAnalyzeStatement(statement, scopes) || right.shouldAnalyzeStatement(statement, scopes)
    is AnalysisStatementFilter.ConfiguringCallFilter -> statement is FunctionCall && statement.args.singleOrNull() is FunctionArgument.Lambda
    is AnalysisStatementFilter.NamedCallFilter -> statement is FunctionCall && statement.name == callName
    is AnalysisStatementFilter.NotFilter -> !negationOf.shouldAnalyzeStatement(statement, scopes)
    is AnalysisStatementFilter.TopLevelElementFilter -> scopes.last().receiver is ObjectOrigin.TopLevelReceiver
}


internal
object AnalysisStatementFiltersImplementations {
    class DefaultAnalyzeEverythingFilter : AnalysisStatementFilter.AnalyzeEverythingFilter

    class DefaultConfiguringCallFilter : AnalysisStatementFilter.ConfiguringCallFilter

    class DefaultTopLevelElementFilter : AnalysisStatementFilter.TopLevelElementFilter

    class DefaultNamedCallFilter(override val callName: String) : AnalysisStatementFilter.NamedCallFilter

    class DefaultNotFilter(override val negationOf: AnalysisStatementFilter) : AnalysisStatementFilter.NotFilter

    class DefaultAndFilter(
        override val left: AnalysisStatementFilter,
        override val right: AnalysisStatementFilter
    ) : AnalysisStatementFilter.CompositionFilter.AndFilter

    class DefaultOrFilter(
        override val left: AnalysisStatementFilter,
        override val right: AnalysisStatementFilter
    ) : AnalysisStatementFilter.CompositionFilter.OrFilter
}
