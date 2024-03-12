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

import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall


fun interface AnalysisStatementFilter {
    fun shouldAnalyzeStatement(statement: DataStatement, scopes: List<AnalysisScopeView>): Boolean

    companion object {
        val isConfiguringCall: AnalysisStatementFilter = AnalysisStatementFilter { statement, _ ->
            statement is FunctionCall && statement.args.singleOrNull() is FunctionArgument.Lambda
        }


        val isTopLevelElement: AnalysisStatementFilter = AnalysisStatementFilter { _, scopes ->
            scopes.last().receiver is ObjectOrigin.TopLevelReceiver
        }


        fun isCallNamed(name: String): AnalysisStatementFilter = AnalysisStatementFilter { statement, _ ->
            statement is FunctionCall && statement.name == name
        }
    }
}


val analyzeEverything: AnalysisStatementFilter = AnalysisStatementFilter { _, _ -> true }


fun AnalysisStatementFilter.and(other: AnalysisStatementFilter): AnalysisStatementFilter = AnalysisStatementFilter { statement, scopes ->
    shouldAnalyzeStatement(statement, scopes) && other.shouldAnalyzeStatement(statement, scopes)
}


fun AnalysisStatementFilter.or(other: AnalysisStatementFilter): AnalysisStatementFilter = AnalysisStatementFilter { statement, scopes ->
    shouldAnalyzeStatement(statement, scopes) || other.shouldAnalyzeStatement(statement, scopes)
}


fun AnalysisStatementFilter.implies(other: AnalysisStatementFilter) = this.not().or(other)


fun AnalysisStatementFilter.not(): AnalysisStatementFilter = AnalysisStatementFilter { statement, scopes ->
    !shouldAnalyzeStatement(statement, scopes)
}
