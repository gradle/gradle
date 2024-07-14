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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.data.nodeDataOf
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.BlockElement
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.LocalValue

object AnalyzedStatementUtils {
    fun produceIsAnalyzedNodeContainer(nodeLanguageTreeOrigin: NodeData<BlockElement>, topLevelBlock: Block, statementFilter: AnalysisStatementFilter) =
        produceIsAnalyzedNodeContainer(nodeLanguageTreeOrigin, collectIncludedStatements(topLevelBlock, statementFilter))

    private fun collectIncludedStatements(topLevelBlock: Block, statementFilter: AnalysisStatementFilter): Set<DataStatement> = buildSet {
        fun directlyNestedStatementsOf(statement: DataStatement): List<DataStatement> = when (statement) {
            is FunctionCall -> statement.args.filterIsInstance<FunctionArgument.Lambda>().flatMap { it.block.statements }
            is Assignment -> directlyNestedStatementsOf(statement.lhs) + directlyNestedStatementsOf(statement.rhs)
            is LocalValue -> directlyNestedStatementsOf(statement.rhs)
            is Expr -> emptyList()
        }

        fun visitStatement(statement: DataStatement, isTopLevel: Boolean) {
            if (statementFilter.shouldAnalyzeStatement(statement, isTopLevel) && add(statement)) {
                directlyNestedStatementsOf(statement).forEach { visitStatement(it, isTopLevel = false) }
            }
        }

        topLevelBlock.statements.forEach { visitStatement(it, isTopLevel = true) }
    }

    private fun produceIsAnalyzedNodeContainer(nodeLanguageTreeOrigin: NodeData<BlockElement>, analyzedStatements: Set<DataStatement>) =
        nodeDataOf {
            nodeLanguageTreeOrigin.data(it).let { element ->
                element is DataStatement && element in analyzedStatements
            }
        }
}

