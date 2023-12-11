package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedLanguageFeature.AnnotationUsage
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedLanguageFeature.LabelledStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.*
import org.jetbrains.kotlin.ElementTypeUtils.getOperationSymbol
import org.jetbrains.kotlin.ElementTypeUtils.isExpression
import org.jetbrains.kotlin.KtNodeTypes.*
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.fir.builder.isUnderscore
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.parsing.*
import org.jetbrains.kotlin.psi.stubs.elements.KtConstantExpressionElementType
import org.jetbrains.kotlin.utils.doNothing

class GrammarToLightTree(private val sourceIdentifier: SourceIdentifier) {

    fun script(tree: LightTree): Syntactic<List<ElementResult<*>>> {
        val scriptNodes = scriptNodes(tree)

        return FailureCollectorContext().run {
            val statements = scriptNodes.map { statement(tree, it) }
            Syntactic(failures + statements)
        }
    }

    private fun statement(tree: LightTree, node: LighterASTNode): ElementResult<DataStatement> =
        when (node.tokenType) { // TODO: filter out whitespace and comments
            LABELED_EXPRESSION -> node.unsupportedBecause(LabelledStatement)
            ANNOTATED_EXPRESSION -> node.unsupportedBecause(AnnotationUsage)
            BINARY_EXPRESSION -> binaryExpression(tree, node)
            CALL_EXPRESSION -> callExpression(tree, node)
            else -> error("Unexpected tokenType in generic statement: ${node.tokenType}")
        }

    private fun propertyAccessStatement(node: LighterASTNode): ElementResult<PropertyAccess> =
        when (node.tokenType) {
            REFERENCE_EXPRESSION -> referenceExpression(node)
            else -> error("Unexpected tokenType in property access statement: ${node.tokenType}")
        }

    private fun expression(tree: LightTree, node: LighterASTNode): ElementResult<Expr> =
        when (node.tokenType) {
            is KtConstantExpressionElementType -> constantExpression(node)
            STRING_TEMPLATE -> stringTemplate(tree, node)
            else -> error("Unexpected tokenType in expression: ${node.tokenType}")
        }

    private fun stringTemplate(tree: LightTree, node: LighterASTNode): ElementResult<Expr> {
        val children = tree.children(node)
        val sb = StringBuilder()
        children.forEach {
            when (it.tokenType) {
                OPEN_QUOTE, CLOSING_QUOTE -> {}
                LITERAL_STRING_TEMPLATE_ENTRY -> sb.append(it.asText)
                else -> error("Unhandled tokenType in string template: ${it.tokenType}")
            }
        }
        return Element(Literal.StringLiteral(sb.toString(), node.data))
    }

    private fun constantExpression(node: LighterASTNode): ElementResult<Expr> {
        val type = node.tokenType
        val text: String = node.asText

        fun reportIncorrectConstant(cause: String): ParsingError {
            return ParsingError(node.data, "Incorrect constant expression, $cause: ${node.asText}")
        }


        val convertedText: Any? = when (type) {
            INTEGER_CONSTANT, FLOAT_CONSTANT -> when {
                hasIllegalUnderscore(text, type) -> return reportIncorrectConstant("illegal underscore")
                else -> parseNumericLiteral(text, type)
            }
            BOOLEAN_CONSTANT -> parseBoolean(text)
            else -> null
        }

        when (type) {
            INTEGER_CONSTANT -> {
                when {
                    convertedText == null -> {
                        return reportIncorrectConstant("missing value")
                    }

                    convertedText !is Long -> return reportIncorrectConstant("illegal constant expression")

                    hasUnsignedLongSuffix(text) || hasLongSuffix(text) -> {
                        if (text.endsWith("l")) {
                            return reportIncorrectConstant("wrong long suffix")
                        }
                        return Element(Literal.LongLiteral(convertedText, node.data))
                    }

                    else -> {
                        return Element(Literal.IntLiteral(convertedText.toInt(), node.data))
                    }
                }
            }
            BOOLEAN_CONSTANT -> return Element(Literal.BooleanLiteral(convertedText as Boolean, node.data))
            else -> error("Unsupported constant type: $type")
        }
    }

    private fun callExpression(tree: LightTree, node: LighterASTNode): ElementResult<Expr> {
        val children = tree.children(node)

        var name: String? = null
        val valueArguments = mutableListOf<LighterASTNode>()
        children.forEach { child ->
            fun process(node: LighterASTNode) {
                when (node.tokenType) {
                    REFERENCE_EXPRESSION -> {
                        name = node.asText
                    }
                    VALUE_ARGUMENT_LIST, LAMBDA_ARGUMENT -> {
                        valueArguments += node
                    }
                    else -> TODO()
                }
            }

            process(child)
        }

        if (name == null) error("Not handled!")

        return elementOrFailure {
            val arguments = valueArguments.flatMap { valueArguments(tree, it) }.map { checkForFailure(it) }
            elementIfNoFailures {
                Element(FunctionCall(null, name!!, arguments.map(::checked), node.data))
            }
        }
    }

    private fun valueArguments(tree: LightTree, node: LighterASTNode): List<SyntacticResult<FunctionArgument.ValueArgument>> {
        val children = tree.children(node)

        val list = mutableListOf<SyntacticResult<FunctionArgument.ValueArgument>>()
        children.forEach {
            when (it.tokenType) {
                VALUE_ARGUMENT -> list.add(valueArgument(tree, it))
                COMMA, LPAR, RPAR -> doNothing()
                else -> TODO()
            }
        }
        return list
    }

    private fun valueArgument(tree: LightTree, node: LighterASTNode): SyntacticResult<FunctionArgument.ValueArgument> =
        syntacticOrFailure {
            val children = tree.children(node)

            var identifier: String? = null
            var expression: FailureCollectorContext.CheckedResult<ElementResult<Expr>>? = null
            children.forEach {
                when (it.tokenType) {
                    VALUE_ARGUMENT_NAME -> identifier = it.asText
                    EQ -> doNothing()
                    is KtConstantExpressionElementType -> expression = checkForFailure(constantExpression(it))
                    CALL_EXPRESSION -> expression = checkForFailure(callExpression(tree, it))
                    else ->
                        if (it.isExpression()) expression = checkForFailure(propertyAccessStatement(it))
                        else TODO()
                }
            }

            collectingFailure(expression ?: node.parsingError("Argument is absent"))

            syntacticIfNoFailures {
                Syntactic(
                    if (identifier != null) FunctionArgument.Named(identifier!!, checked(expression!!), node.data)
                    else FunctionArgument.Positional(checked(expression!!), node.data)
                )
            }

        }

    private fun binaryExpression(tree: LightTree, node: LighterASTNode): ElementResult<Assignment> {
        val children = tree.children(node)

        var isLeftArgument = true
        lateinit var operationTokenName: String
        var leftArg: LighterASTNode? = null
        var rightArg: LighterASTNode? = null

        children.forEach {
            when (it.tokenType) {
                OPERATION_REFERENCE -> {
                    isLeftArgument = false
                    operationTokenName = it.asText
                }
                else -> if (it.isExpression()) {
                    if (isLeftArgument) {
                        leftArg = it
                    } else {
                        rightArg = it
                    }
                }
            }
        }

        val operationToken = operationTokenName.getOperationSymbol()

        if (operationToken != EQ) return node.unsupportedBecause(UnsupportedLanguageFeature.UnsupportedOperationInBinaryExpression)
        if (leftArg == null) return node.parsingError("Missing left hand side in binary expression")
        if (rightArg == null) return node.parsingError("Missing right hand side in binary expression")

        return elementOrFailure {
            val lhs = checkForFailure(propertyAccessStatement(leftArg!!))
            val expr = checkForFailure(expression(tree, rightArg!!))

            elementIfNoFailures {
                Element(Assignment(checked(lhs), checked(expr), node.data))
            }
        }
    }

    private fun referenceExpression(node: LighterASTNode): ElementResult<PropertyAccess> {
        val name = node.asText
        if (name.isUnderscore) return node.parsingError("Underscore usage without backticks in reference expression: $name")
        return Element(PropertyAccess(null, name, node.data))
    }

    private fun scriptNodes(tree: LightTree): List<LighterASTNode> {
        // TODO: the actual script we want to parse is wrapped into a class initializer block, we need to extract it

        val root = tree.root

        val childrenOfRoot = tree.children(root)

        // TODO: not checking for package, import or shebang statements because our script-into-class wrapping doesn't allow for those

        val wrappingClass = childrenOfRoot.expectSingleOfKind(CLASS)
        val childrenOfWrappingClass = tree.children(wrappingClass)
        val wrappingClassBody = childrenOfWrappingClass.expectSingleOfKind(CLASS_BODY)
        val childrenOfWrappingClassBody = tree.children(wrappingClassBody)
        val wrappingClassInitializer = childrenOfWrappingClassBody.expectSingleOfKind(CLASS_INITIALIZER)
        val childrenOfWrappingClassInitializer = tree.children(wrappingClassInitializer)
        val wrappingClassInitializerBlock = childrenOfWrappingClassInitializer.expectSingleOfKind(BLOCK)
        val childrenOfWrappingClassInitializerBlock = tree.children(wrappingClassInitializerBlock)

        return extractBlockContent(childrenOfWrappingClassInitializerBlock)
    }

    private fun extractBlockContent(blockNodes: List<LighterASTNode>): List<LighterASTNode> {
        check(blockNodes.size >= 2) // first and last nodes are the opening and closing braces

        val openBrace = blockNodes.first()
        openBrace.expectKind(LBRACE)

        val closingBrace = blockNodes.last()
        closingBrace.expectKind(RBRACE)

        return blockNodes.slice(1..blockNodes.size - 2)
    }

    private val LighterASTNode.data get() = sourceData(sourceIdentifier)

    private fun LighterASTNode.unsupportedBecause(feature: UnsupportedLanguageFeature) =
        UnsupportedConstruct(
            this.data,
            this.data,
            feature
        ) // TODO: two sources are identical, not like in the other parser

    private fun LighterASTNode.parsingError(message: String) =
        ParsingError(this.data, message)

}

