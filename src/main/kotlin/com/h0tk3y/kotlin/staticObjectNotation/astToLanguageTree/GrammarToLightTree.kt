package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.FailureCollectorContext.CheckedResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedLanguageFeature.AnnotationUsage
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedLanguageFeature.LabelledStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.*
import org.jetbrains.kotlin.ElementTypeUtils.getOperationSymbol
import org.jetbrains.kotlin.ElementTypeUtils.isExpression
import org.jetbrains.kotlin.KtNodeTypes.*
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.psi.TokenType
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.parsing.*
import org.jetbrains.kotlin.psi.stubs.elements.KtConstantExpressionElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtNameReferenceExpressionElementType
import org.jetbrains.kotlin.utils.doNothing

class GrammarToLightTree(private val sourceIdentifier: SourceIdentifier) {

    fun script(tree: LightTree): Syntactic<List<ElementResult<*>>> {
        val scriptNodes = scriptNodes(tree)
        val importNodes = importNodes(tree)

        return FailureCollectorContext().run {
            val imports = importNodes.map { import(tree, it) }
            val statements = scriptNodes.map { statement(tree, it) }
            Syntactic(failures + imports + statements)
        }
    }

    private fun import(tree: LightTree, node: LighterASTNode): ElementResult<Import> =
        elementOrFailure {
            val children = tree.children(node)

            var content: CheckedResult<ElementResult<PropertyAccess>>? = null
            var contentNodeSource: LightTreeSourceData? = null
            children.forEach {
                when (it.tokenType) {
                    DOT_QUALIFIED_EXPRESSION, REFERENCE_EXPRESSION -> {
                        contentNodeSource  = it.data
                        content = checkForFailure(propertyAccessStatement(tree, it))
                    }
                }
            }

            collectingFailure(content ?: node.parsingError("Qualified expression without selector"))

            elementIfNoFailures {
                fun PropertyAccess.flatten(): List<String> =
                    buildList {
                        if (receiver is PropertyAccess) {
                            addAll(receiver.flatten())
                        }
                        add(name)
                    }

                val nameParts = checked(content!!).flatten()
                Element(Import(AccessChain(nameParts, contentNodeSource!!), node.data)) //TODO
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

    private fun expression(tree: LightTree, node: LighterASTNode): ElementResult<Expr> =
        when (node.tokenType) {
            in QUALIFIED_ACCESS, REFERENCE_EXPRESSION -> propertyAccessStatement(tree, node)
            is KtConstantExpressionElementType -> constantExpression(node)
            STRING_TEMPLATE -> stringTemplate(tree, node)
            else -> error("Unexpected tokenType in expression: ${node.tokenType}")
        }

    private fun propertyAccessStatement(tree: LightTree, node: LighterASTNode): ElementResult<PropertyAccess> =
        when (node.tokenType) {
            REFERENCE_EXPRESSION -> Element(PropertyAccess(null, referenceExpression(node).value, node.data))
            in QUALIFIED_ACCESS -> qualifiedExpression(tree, node)
            else -> error("Unexpected tokenType in property access statement: ${node.tokenType}")
        }

    private fun qualifiedExpression(tree: LightTree, node: LighterASTNode): ElementResult<PropertyAccess> =
        elementOrFailure {
            val children = tree.children(node)

            var isSelector = false
            var selector: Syntactic<String>? = null
            var receiver: CheckedResult<ElementResult<Expr>>? = null //before dot
            children.forEach {
                when (val tokenType = it.tokenType) {
                    DOT -> isSelector = true
                    SAFE_ACCESS -> {
                        collectingFailure(node.unsupportedBecause(UnsupportedLanguageFeature.SafeNavigation))
                    }
                    else -> {
                        val isEffectiveSelector = isSelector && tokenType != TokenType.ERROR_ELEMENT
                        if (isEffectiveSelector) {
                            val callExpressionCallee = if (tokenType == CALL_EXPRESSION) tree.getFirstChildExpressionUnwrapped(it) else null
                                if (tokenType is KtNameReferenceExpressionElementType || (tokenType == CALL_EXPRESSION && callExpressionCallee?.tokenType != LAMBDA_EXPRESSION)) {
                                    selector = referenceExpression(it)
                                } else {
                                    collectingFailure(node.parsingError("The expression cannot be a selector (occur after a dot)"))
                                }
                        } else {
                            receiver = checkForFailure(expression(tree, it))
                        }
                    }
                }
            }

            collectingFailure(selector ?: node.parsingError("Qualified expression without selector"))
            collectingFailure(receiver ?: node.parsingError("Qualified expression without receiver"))

            elementIfNoFailures {
                Element(PropertyAccess(checked(receiver!!), selector!!.value, node.data))
            }
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
            var expression: CheckedResult<ElementResult<Expr>>? = null
            children.forEach {
                when (it.tokenType) {
                    VALUE_ARGUMENT_NAME -> identifier = it.asText
                    EQ -> doNothing()
                    is KtConstantExpressionElementType -> expression = checkForFailure(constantExpression(it))
                    CALL_EXPRESSION -> expression = checkForFailure(callExpression(tree, it))
                    else ->
                        if (it.isExpression()) expression = checkForFailure(expression(tree, it))
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
            val lhs = checkForFailure(propertyAccessStatement(tree, leftArg!!))
            val expr = checkForFailure(expression(tree, rightArg!!))

            elementIfNoFailures {
                Element(Assignment(checked(lhs), checked(expr), node.data))
            }
        }
    }

    private fun referenceExpression(node: LighterASTNode): Syntactic<String> = Syntactic(node.asText)

    private fun importNodes(tree: LightTree): List<LighterASTNode> {
        // the actual script we want to parse is wrapped into a class initializer block, we need to extract it
        val root = tree.root
        val childrenOfRoot = tree.children(root)
        return tree.children(childrenOfRoot.expectSingleOfKind(IMPORT_LIST))
    }

    private fun scriptNodes(tree: LightTree): List<LighterASTNode> {
        // the actual script we want to parse is wrapped into a class initializer block, we need to extract it

        val root = tree.root
        val childrenOfRoot = tree.children(root)
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

