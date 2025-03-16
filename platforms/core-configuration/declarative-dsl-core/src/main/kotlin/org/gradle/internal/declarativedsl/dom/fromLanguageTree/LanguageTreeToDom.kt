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

package org.gradle.internal.declarativedsl.dom.fromLanguageTree

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DefaultElementNode
import org.gradle.internal.declarativedsl.dom.DefaultErrorNode
import org.gradle.internal.declarativedsl.dom.DefaultLiteralNode
import org.gradle.internal.declarativedsl.dom.DefaultNamedReferenceNode
import org.gradle.internal.declarativedsl.dom.DefaultPropertyNode
import org.gradle.internal.declarativedsl.dom.DefaultValueFactoryNode
import org.gradle.internal.declarativedsl.dom.DocumentError
import org.gradle.internal.declarativedsl.dom.SyntaxError
import org.gradle.internal.declarativedsl.dom.UnsupportedKotlinFeature
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntax
import org.gradle.internal.declarativedsl.dom.UnsupportedSyntaxCause
import org.gradle.internal.declarativedsl.dom.data.NodeDataContainer
import org.gradle.internal.declarativedsl.dom.data.ValueData
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
import org.gradle.internal.declarativedsl.language.NamedReference
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.ParsingError
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.This
import org.gradle.internal.declarativedsl.language.UnsupportedConstruct


fun LanguageTreeResult.toDocument(): LanguageTreeBackedDocument = convertBlockToDocument(topLevelBlock)


fun convertBlockToDocument(block: Block): LanguageTreeBackedDocument {
    val context = LanguageTreeToDomContext()
    val content = block.content.map(context::blockElementToNode)
    return LanguageTreeBackedDocument(block, context.languageTreeMappingContainer, content)
}


interface LanguageTreeMappingContainer : NodeDataContainer<BlockElement, FunctionCall, Assignment, BlockElement>, ValueData<Expr>


private
class LanguageTreeToDomContext {
    private
    val nodeMapping: MutableMap<DeclarativeDocument.DocumentNode, BlockElement> = mutableMapOf()

    private
    val valueMapping: MutableMap<DeclarativeDocument.ValueNode, Expr> = mutableMapOf()

    val languageTreeMappingContainer = object : LanguageTreeMappingContainer {
        override fun data(node: DeclarativeDocument.DocumentNode.ElementNode): FunctionCall = nodeMapping.getValue(node) as FunctionCall
        override fun data(node: DeclarativeDocument.DocumentNode.PropertyNode): Assignment = nodeMapping.getValue(node) as Assignment
        override fun data(node: DeclarativeDocument.DocumentNode.ErrorNode): BlockElement = nodeMapping.getValue(node)
        override fun data(node: DeclarativeDocument.ValueNode.ValueFactoryNode): Expr = valueMapping.getValue(node)
        override fun data(node: DeclarativeDocument.ValueNode.LiteralValueNode): Expr = valueMapping.getValue(node)
        override fun data(node: DeclarativeDocument.ValueNode.NamedReferenceNode): Expr = valueMapping.getValue(node)
    }

    fun blockElementToNode(blockElement: BlockElement): DeclarativeDocument.DocumentNode = when (blockElement) {
        is Assignment -> {
            if ((blockElement.lhs.receiver != null))
                errorDocumentNode(blockElement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.AssignmentWithExplicitReceiver)))
            else {
                when (val rhs = exprToValue(blockElement.rhs)) {
                    is ExprConversion.Failed -> errorDocumentNode(blockElement, rhs.errors)
                    is ExprConversion.Converted -> propertyDocumentNode(blockElement, rhs.valueNode)
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
                errorDocumentNode(blockElement, errors) // TODO: reason
            } else {
                val arguments = values.map { (it as ExprConversion.Converted).valueNode }
                elementDocumentNode(blockElement, arguments, content)
            }
        }

        is LocalValue -> errorDocumentNode(blockElement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.LocalVal)))
        is Expr -> errorDocumentNode(blockElement, listOf(UnsupportedSyntax(UnsupportedSyntaxCause.DanglingExpr)))

        is ErroneousStatement -> errorDocumentNode(blockElement, mapBlockElementErrors(blockElement.failingResult))
    }.also { nodeMapping[it] = blockElement }


    private
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
        data class Converted(val valueNode: DeclarativeDocument.ValueNode) : ExprConversion
        data class Failed(val errors: Collection<DocumentError>) : ExprConversion
    }


    @Suppress("NestedBlockDepth")
    private
    fun exprToValue(expr: Expr): ExprConversion = when (expr) {
        is Literal<*> -> ExprConversion.Converted(literalNode(expr))
        is FunctionCall -> run {
            val errors = mutableListOf<DocumentError>()
            // Check for receiver: if it is a complex expression, it cannot be expressed in a DOM; but we can handle access chains as chained names
            val name = when (expr.receiver) {
                null -> expr.name
                is NamedReference ->
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

            if (expr.args.any { it is FunctionArgument.SingleValueArgument && it.expr is NamedReference }) {
                errors += UnsupportedSyntax(UnsupportedSyntaxCause.ValueFactoryCallWithNamedReference)
            }

            val values = expr.args.filterIsInstance<FunctionArgument.Positional>().map { exprToValue(it.expr) }
            errors += values.filterIsInstance<ExprConversion.Failed>().flatMap { it.errors }
            if (errors.isEmpty()) {
                checkNotNull(name)
                ExprConversion.Converted(DefaultValueFactoryNode(name, expr.sourceData, values.map { (it as ExprConversion.Converted).valueNode }))
            } else {
                ExprConversion.Failed(errors)
            }
        }

        is NamedReference -> when {
            expr.receiver != null -> ExprConversion.Failed(listOf(UnsupportedSyntax(UnsupportedSyntaxCause.NamedReferenceWithExplicitReceiver)))
            else -> ExprConversion.Converted(namedReferenceNode(expr))
        }

        is Null -> ExprConversion.Failed(listOf(UnsupportedSyntax(UnsupportedSyntaxCause.UnsupportedNullValue)))
        is This -> ExprConversion.Failed(listOf(UnsupportedSyntax(UnsupportedSyntaxCause.UnsupportedThisValue)))
    }.also { (it as? ExprConversion.Converted)?.let { converted -> valueMapping[converted.valueNode] = expr } }

    private
    fun Expr.asChainedNameOrNull(): String? {
        fun recurse(e: Expr): StringBuilder? = when (e) {
            is NamedReference -> {
                when (e.receiver) {
                    null -> StringBuilder(e.name)
                    else -> recurse(e.receiver)?.apply { append(".${e.name}") }
                }
            }

            else -> null
        }
        return recurse(this)?.toString()
    }
}


class LanguageTreeBackedDocument internal constructor(
    val block: Block,
    val languageTreeMappingContainer: LanguageTreeMappingContainer,
    override val content: List<DeclarativeDocument.DocumentNode>
) : DeclarativeDocument {

    override val sourceData: SourceData
        get() = block.sourceData
}


private
fun propertyDocumentNode(blockElement: Assignment, valueNode: DeclarativeDocument.ValueNode) =
    DefaultPropertyNode(blockElement.lhs.name, blockElement.sourceData, valueNode)


private
fun elementDocumentNode(blockElement: FunctionCall, arguments: List<DeclarativeDocument.ValueNode>, content: List<DeclarativeDocument.DocumentNode>) =
    DefaultElementNode(blockElement.name, blockElement.sourceData, arguments, content)


private
fun errorDocumentNode(blockElement: BlockElement, errors: Collection<DocumentError>): DefaultErrorNode {
    /** Workaround: we do not have the source data in [ErroneousStatement]s yet, so we have to get it from the [FailingResult]s inside those. */
    val sourceData = if (blockElement is ErroneousStatement) {
        fun source(failingResult: FailingResult): SourceData = when (failingResult) {
            is MultipleFailuresResult -> source(failingResult.failures.first())
            is ParsingError -> failingResult.potentialElementSource
            is UnsupportedConstruct -> failingResult.potentialElementSource
        }
        source(blockElement.failingResult)
    } else blockElement.sourceData

    return DefaultErrorNode(sourceData, errors)
}


private
fun literalNode(literal: Literal<*>) =
    DefaultLiteralNode(literal.value, literal.sourceData)


private
fun namedReferenceNode(namedReference: NamedReference) =
    DefaultNamedReferenceNode(namedReference.name, namedReference.sourceData)
