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

package org.gradle.internal.declarativedsl.dom

import org.gradle.internal.declarativedsl.dom.LanguageTreeBackedDocument.BlockElementBackedDocumentNode.AssignmentBackedPropertyNode
import org.gradle.internal.declarativedsl.dom.LanguageTreeBackedDocument.BlockElementBackedDocumentNode.BlockElementBackedErrorNode
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.BlockElement
import org.gradle.internal.declarativedsl.language.ErroneousStatement
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FailingResult
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.MultipleFailuresResult
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.ParsingError
import org.gradle.internal.declarativedsl.language.PropertyAccess
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.language.This
import org.gradle.internal.declarativedsl.language.UnsupportedConstruct


// TODO: imports are ignored for now; we should instead represent them as unsupported feature usage in the document
fun LanguageTreeResult.toDocument(): DeclarativeDocument = convertBlockToDocument(topLevelBlock)


fun convertBlockToDocument(block: Block): DeclarativeDocument = LanguageTreeBackedDocument(block, block.content.map(::blockElementToNode))


private
fun blockElementToNode(blockElement: BlockElement): LanguageTreeBackedDocument.BlockElementBackedDocumentNode = when (blockElement) {
    is Assignment -> {
        if ((blockElement.lhs.receiver != null))
            BlockElementBackedErrorNode(blockElement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.AssignmentWithExplicitReceiver)))
        else {
            when (val rhs = exprToValue(blockElement.rhs)) {
                is ExprConversion.Failed -> BlockElementBackedErrorNode(blockElement, rhs.errors)
                is ExprConversion.Converted -> AssignmentBackedPropertyNode(blockElement, rhs.valueNode)
            }
        }
    }

    is FunctionCall -> {
        val errors = mutableListOf<DocumentError>()
        if (blockElement.receiver != null) {
            errors += UnsupportedSyntax(UnsupportedSyntaxCause.ElementWithExplicitReceiver)
        }
        if (blockElement.args.any { it is FunctionArgument.Named }) {
            errors += UnsupportedSyntax(UnsupportedSyntaxCause.ElementArgumentFormat)
        }
        val lambdas = blockElement.args.filterIsInstance<FunctionArgument.Lambda>()

        val lambda = when (lambdas.size) {
            0 -> null
            1 -> lambdas.single()
            else -> {
                errors += UnsupportedSyntax(UnsupportedSyntaxCause.ElementMultipleLambdas)
                null
            }
        }
        val content = lambda?.block?.content.orEmpty().map { blockElementToNode(it) }
        val values = blockElement.args.filterIsInstance<FunctionArgument.Positional>().map { exprToValue(it.expr) }
        errors += values.filterIsInstance<ExprConversion.Failed>().flatMap { it.errors }

        if (errors.isNotEmpty()) {
            BlockElementBackedErrorNode(blockElement, errors) // TODO: reason
        } else {
            val arguments = values.map { (it as ExprConversion.Converted).valueNode }
            LanguageTreeBackedDocument.BlockElementBackedDocumentNode.FunctionCallBackedElementNode(blockElement, arguments, content)
        }
    }

    is LocalValue -> BlockElementBackedErrorNode(blockElement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.LocalVal)))
    is Expr -> BlockElementBackedErrorNode(blockElement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.DanglingExpr)))

    is ErroneousStatement -> BlockElementBackedErrorNode(blockElement, mapBlockElementErrors(blockElement.failingResult))
}


fun mapBlockElementErrors(failingResult: FailingResult): Collection<DocumentError> {
    return buildList {
        fun visit(result: FailingResult) {
            when (result) {
                is MultipleFailuresResult -> result.failures.forEach(::visit)
                is ParsingError -> add(SyntaxError(result))
                is UnsupportedConstruct -> add(UnsupportedKotlinFeature(result))
            }
        }
        visit(failingResult)
    }
}


private
sealed interface ExprConversion {
    data class Converted(val valueNode: LanguageTreeBackedDocument.ExprBackedValueNode) : ExprConversion
    data class Failed(val errors: Collection<DocumentError>) : ExprConversion
}


private
fun exprToValue(expr: Expr): ExprConversion = when (expr) {
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


private
fun Expr.asChainedNameOrNull(): String? {
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


private
class LanguageTreeBackedDocument(
    val block: Block,
    override val content: Collection<BlockElementBackedDocumentNode>
) : DeclarativeDocument {

    override val sourceIdentifier: SourceIdentifier
        get() = block.sourceData.sourceIdentifier

    sealed interface BlockElementBackedDocumentNode : DeclarativeDocument.DocumentNode {

        val blockElement: BlockElement

        override val sourceData: SourceData
            get() = blockElement.sourceData

        class AssignmentBackedPropertyNode(
            override val blockElement: Assignment,
            override val value: ExprBackedValueNode

        ) : DeclarativeDocument.DocumentNode.PropertyNode, BlockElementBackedDocumentNode {
            override val name: String
                get() = blockElement.lhs.name
        }

        data class FunctionCallBackedElementNode(
            override val blockElement: FunctionCall,
            override val elementValues: Collection<DeclarativeDocument.ValueNode>,
            override val content: Collection<DeclarativeDocument.DocumentNode>,
        ) : DeclarativeDocument.DocumentNode.ElementNode, BlockElementBackedDocumentNode {
            override val name: String = blockElement.name
        }

        data class BlockElementBackedErrorNode(
            override val blockElement: BlockElement,
            override val errors: Collection<DocumentError>
        ) : DeclarativeDocument.DocumentNode.ErrorNode, BlockElementBackedDocumentNode {
            override val sourceData: SourceData
                /** Workaround: we do not have the source data in [ErroneousStatement]s yet, so we have to get it from the [FailingResult]s inside those. */
                get() = if (blockElement is ErroneousStatement) {
                    fun source(failingResult: FailingResult): SourceData = when (failingResult) {
                        is MultipleFailuresResult -> source(failingResult.failures.first())
                        is ParsingError -> failingResult.potentialElementSource
                        is UnsupportedConstruct -> failingResult.potentialElementSource
                    }
                    source(blockElement.failingResult)
                } else super.sourceData
        }
    }

    sealed interface ExprBackedValueNode : DeclarativeDocument.ValueNode {

        val expr: Expr

        override val sourceData: SourceData
            get() = expr.sourceData

        data class LiteralBackedLiteralValueNode(override val expr: Literal<*>) : DeclarativeDocument.ValueNode.LiteralValueNode, ExprBackedValueNode {
            override val value: Any
                get() = expr.value
        }
        data class FunctionCallBackedValueFactoryNode(
            override val factoryName: String,
            override val expr: FunctionCall,
            override val values: List<DeclarativeDocument.ValueNode>
        ) : DeclarativeDocument.ValueNode.ValueFactoryNode, ExprBackedValueNode
    }
}


internal
fun DeclarativeDocument.DocumentNode.blockElement(): BlockElement = when (this) {
    is LanguageTreeBackedDocument.BlockElementBackedDocumentNode -> blockElement
    else -> throw IllegalStateException("cannot run document resolution with documents not produced from declarative DSL")
}


internal
fun DeclarativeDocument.ValueNode.expr(): Expr = when (this) {
    is LanguageTreeBackedDocument.ExprBackedValueNode -> expr
    else -> throw IllegalStateException("cannot run document resolution with documents not produced from declarative DSL")
}
