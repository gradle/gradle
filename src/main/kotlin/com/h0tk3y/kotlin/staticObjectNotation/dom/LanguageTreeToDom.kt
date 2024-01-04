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

package com.h0tk3y.kotlin.staticObjectNotation.dom

import com.h0tk3y.kotlin.staticObjectNotation.dom.LanguageTreeBackedDocument.StatementBackedDocumentNode.AssignmentBackedPropertyNode
import com.h0tk3y.kotlin.staticObjectNotation.dom.LanguageTreeBackedDocument.StatementBackedDocumentNode.StatementBackedErrorNode
import com.h0tk3y.kotlin.staticObjectNotation.language.Assignment
import com.h0tk3y.kotlin.staticObjectNotation.language.Block
import com.h0tk3y.kotlin.staticObjectNotation.language.DataStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.Expr
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionArgument
import com.h0tk3y.kotlin.staticObjectNotation.language.FunctionCall
import com.h0tk3y.kotlin.staticObjectNotation.language.Literal
import com.h0tk3y.kotlin.staticObjectNotation.language.LocalValue
import com.h0tk3y.kotlin.staticObjectNotation.language.Null
import com.h0tk3y.kotlin.staticObjectNotation.language.PropertyAccess
import com.h0tk3y.kotlin.staticObjectNotation.language.This

fun convertBlockToDocument(block: Block): DeclarativeDocument = LanguageTreeBackedDocument(block, block.statements.map(::statementToNode))

private fun statementToNode(statement: DataStatement): LanguageTreeBackedDocument.StatementBackedDocumentNode = when (statement) {
    is Assignment -> {
        if ((statement.lhs.receiver != null))
            StatementBackedErrorNode(statement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.AssignmentWithExplicitReceiver)))
        else {
            when (val rhs = exprToValue(statement.rhs)) {
                is ExprConversion.Failed -> StatementBackedErrorNode(statement, rhs.errors)
                is ExprConversion.Converted -> AssignmentBackedPropertyNode(statement, rhs.valueNode)
            }
        }
    }

    is FunctionCall -> {
        val errors = mutableListOf<DocumentError>()
        if (statement.receiver != null) {
            errors += UnsupportedSyntax(UnsupportedSyntaxCause.ElementWithExplicitReceiver)
        }
        if (statement.args.any { it is FunctionArgument.Named }) {
            errors += UnsupportedSyntax(UnsupportedSyntaxCause.ElementArgumentFormat)
        }
        val lambdas = statement.args.filterIsInstance<FunctionArgument.Lambda>()

        val lambda = when (lambdas.size) {
            0 -> null
            1 -> lambdas.single()
            else -> {
                errors += UnsupportedSyntax(UnsupportedSyntaxCause.ElementMultipleLambdas)
                null
            }
        }
        val content = lambda?.block?.statements.orEmpty().map { statementToNode(it) }
        val values = statement.args.filterIsInstance<FunctionArgument.Positional>().map { exprToValue(it.expr) }
        errors += values.filterIsInstance<ExprConversion.Failed>().flatMap { it.errors }

        if (errors.isNotEmpty()) {
            StatementBackedErrorNode(statement, errors) // TODO: reason
        } else {
            val arguments = values.map { (it as ExprConversion.Converted).valueNode }
            LanguageTreeBackedDocument.StatementBackedDocumentNode.FunctionCallBackedElementNode(statement, arguments, content)
        }
    }

    is LocalValue -> StatementBackedErrorNode(statement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.LocalVal)))
    is Expr -> StatementBackedErrorNode(statement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.DanglingExpr)))
}

private sealed interface ExprConversion {
    data class Converted(val valueNode: LanguageTreeBackedDocument.ExprBackedValueNode) : ExprConversion
    data class Failed(val errors: Collection<DocumentError>) : ExprConversion
}

private fun exprToValue(expr: Expr): ExprConversion = when (expr) {
    is Literal<*> -> ExprConversion.Converted(LanguageTreeBackedDocument.ExprBackedValueNode.LiteralBackedLiteralValueNode(expr))
    is FunctionCall -> run {
        val errors = mutableListOf<DocumentError>()
        // Check for receiver: if it is a complex expression, it cannot be expressed in a DOM; but we can handle access chains as chained names
        val name = when (expr.receiver) {
            null -> expr.name
            is PropertyAccess ->
                expr.receiver.asChainedNameOrNull()?.plus(".${expr.name}") ?: run {
                    errors += UnsupportedSyntax(UnsupportedSyntaxCause.ValueFactoryCallWithComplexReceiver)
                    null
                }

            else -> {
                errors += UnsupportedSyntax(UnsupportedSyntaxCause.ValueFactoryCallWithComplexReceiver)
                null
            }
        }
        if (expr.args.any { it is FunctionArgument.Named || it is FunctionArgument.Lambda }) {
            errors += UnsupportedSyntax(UnsupportedSyntaxCause.ValueFactoryArgumentFormat)
        }

        val values = expr.args.filterIsInstance<FunctionArgument.Positional>().map { exprToValue(it.expr) }
        errors += values.filterIsInstance<ExprConversion.Failed>().flatMap { it.errors }
        if (errors.isEmpty()) {
            checkNotNull(name)
            ExprConversion.Converted(LanguageTreeBackedDocument.ExprBackedValueNode.FunctionCallBackedValueFactoryNode(name, expr, values.map { (it as ExprConversion.Converted).valueNode }))
        } else {
            ExprConversion.Failed(errors)
        }
    }

    is PropertyAccess -> ExprConversion.Failed(listOf(UnsupportedSyntax(UnsupportedSyntaxCause.UnsupportedPropertyAccess)))
    is Null -> ExprConversion.Failed(listOf(UnsupportedSyntax(UnsupportedSyntaxCause.UnsupportedNullValue)))
    is This -> ExprConversion.Failed(listOf(UnsupportedSyntax(UnsupportedSyntaxCause.UnsupportedThisValue)))
}

private fun Expr.asChainedNameOrNull(): String? {
    fun recurse(e: Expr): StringBuilder? = when (e) {
        is PropertyAccess -> {
            when (e.receiver) {
                null -> StringBuilder(e.name)
                else -> recurse(e.receiver)?.apply { append(".${e.name}") }
            }
        }

        else -> null
    }
    return recurse(this)?.toString()
}

private class LanguageTreeBackedDocument(
    val block: Block,
    override val content: Collection<StatementBackedDocumentNode>
) : DeclarativeDocument {

    sealed interface StatementBackedDocumentNode : DeclarativeDocument.DocumentNode {
        val statement: DataStatement

        class AssignmentBackedPropertyNode(
            override val statement: Assignment,
            override val value: ExprBackedValueNode

        ) : DeclarativeDocument.DocumentNode.PropertyNode, StatementBackedDocumentNode {
            override val name: String get() = statement.lhs.name
        }

        data class FunctionCallBackedElementNode(
            override val statement: FunctionCall,
            override val elementValues: Collection<DeclarativeDocument.ValueNode>,
            override val content: Collection<DeclarativeDocument.DocumentNode>,
        ) : DeclarativeDocument.DocumentNode.ElementNode, StatementBackedDocumentNode {
            override val name: String = statement.name
        }

        data class StatementBackedErrorNode(
            override val statement: DataStatement,
            override val errors: Collection<DocumentError>
        ) : DeclarativeDocument.DocumentNode.ErrorNode, StatementBackedDocumentNode

        data class SyntaxErrorNode(
            override val errors: Collection<SyntaxError>
        ) : DeclarativeDocument.DocumentNode.ErrorNode
    }

    sealed interface ExprBackedValueNode : DeclarativeDocument.ValueNode {
        val expr: Expr

        data class LiteralBackedLiteralValueNode(override val expr: Literal<*>) : DeclarativeDocument.ValueNode.LiteralValueNode, ExprBackedValueNode {
            override val value: Any get() = expr.value
        }

        data class FunctionCallBackedValueFactoryNode(
            override val factoryName: String,
            override val expr: FunctionCall,
            override val values: List<DeclarativeDocument.ValueNode>
        ) : DeclarativeDocument.ValueNode.ValueFactoryNode, ExprBackedValueNode
    }
}
