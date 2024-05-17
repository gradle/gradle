package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.AccessChain
import org.gradle.internal.declarativedsl.language.Assignment
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.Element
import org.gradle.internal.declarativedsl.language.ElementResult
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FailingResult
import org.gradle.internal.declarativedsl.language.FunctionArgument
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.Import
import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.language.Literal
import org.gradle.internal.declarativedsl.language.LocalValue
import org.gradle.internal.declarativedsl.language.Null
import org.gradle.internal.declarativedsl.language.ParsingError
import org.gradle.internal.declarativedsl.language.PropertyAccess
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.language.Syntactic
import org.gradle.internal.declarativedsl.language.SyntacticResult
import org.gradle.internal.declarativedsl.language.This
import org.gradle.internal.declarativedsl.language.UnsupportedConstruct
import org.gradle.internal.declarativedsl.language.UnsupportedLanguageFeature
import org.gradle.internal.declarativedsl.parsing.FailureCollectorContext.CheckedResult
import org.jetbrains.kotlin.ElementTypeUtils.getOperationSymbol
import org.jetbrains.kotlin.ElementTypeUtils.isExpression
import org.jetbrains.kotlin.KtNodeTypes.ANNOTATED_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.ANNOTATION_ENTRY
import org.jetbrains.kotlin.KtNodeTypes.ARRAY_ACCESS_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.BINARY_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.BLOCK
import org.jetbrains.kotlin.KtNodeTypes.BOOLEAN_CONSTANT
import org.jetbrains.kotlin.KtNodeTypes.CALL_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.CLASS
import org.jetbrains.kotlin.KtNodeTypes.CLASS_BODY
import org.jetbrains.kotlin.KtNodeTypes.CLASS_INITIALIZER
import org.jetbrains.kotlin.KtNodeTypes.DOT_QUALIFIED_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.FUN
import org.jetbrains.kotlin.KtNodeTypes.FUNCTION_LITERAL
import org.jetbrains.kotlin.KtNodeTypes.IMPORT_ALIAS
import org.jetbrains.kotlin.KtNodeTypes.IMPORT_LIST
import org.jetbrains.kotlin.KtNodeTypes.INTEGER_CONSTANT
import org.jetbrains.kotlin.KtNodeTypes.LABELED_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.LAMBDA_ARGUMENT
import org.jetbrains.kotlin.KtNodeTypes.LAMBDA_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.LITERAL_STRING_TEMPLATE_ENTRY
import org.jetbrains.kotlin.KtNodeTypes.MODIFIER_LIST
import org.jetbrains.kotlin.KtNodeTypes.NULL
import org.jetbrains.kotlin.KtNodeTypes.OPERATION_REFERENCE
import org.jetbrains.kotlin.KtNodeTypes.PACKAGE_DIRECTIVE
import org.jetbrains.kotlin.KtNodeTypes.PARENTHESIZED
import org.jetbrains.kotlin.KtNodeTypes.PREFIX_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.PROPERTY
import org.jetbrains.kotlin.KtNodeTypes.REFERENCE_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.STRING_TEMPLATE
import org.jetbrains.kotlin.KtNodeTypes.THIS_EXPRESSION
import org.jetbrains.kotlin.KtNodeTypes.TYPEALIAS
import org.jetbrains.kotlin.KtNodeTypes.TYPE_REFERENCE
import org.jetbrains.kotlin.KtNodeTypes.VALUE_ARGUMENT
import org.jetbrains.kotlin.KtNodeTypes.VALUE_ARGUMENT_LIST
import org.jetbrains.kotlin.KtNodeTypes.VALUE_ARGUMENT_NAME
import org.jetbrains.kotlin.KtNodeTypes.VALUE_PARAMETER_LIST
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderImpl
import org.jetbrains.kotlin.com.intellij.psi.TokenType.ERROR_ELEMENT
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens.ARROW
import org.jetbrains.kotlin.lexer.KtTokens.CLOSING_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.COLON
import org.jetbrains.kotlin.lexer.KtTokens.COMMA
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.lexer.KtTokens.EQ
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.INTEGER_LITERAL
import org.jetbrains.kotlin.lexer.KtTokens.LBRACE
import org.jetbrains.kotlin.lexer.KtTokens.LPAR
import org.jetbrains.kotlin.lexer.KtTokens.MUL
import org.jetbrains.kotlin.lexer.KtTokens.OPEN_QUOTE
import org.jetbrains.kotlin.lexer.KtTokens.QUALIFIED_ACCESS
import org.jetbrains.kotlin.lexer.KtTokens.RBRACE
import org.jetbrains.kotlin.lexer.KtTokens.RPAR
import org.jetbrains.kotlin.lexer.KtTokens.SAFE_ACCESS
import org.jetbrains.kotlin.parsing.hasIllegalUnderscore
import org.jetbrains.kotlin.parsing.hasLongSuffix
import org.jetbrains.kotlin.parsing.hasUnsignedLongSuffix
import org.jetbrains.kotlin.parsing.parseBoolean
import org.jetbrains.kotlin.parsing.parseNumericLiteral
import org.jetbrains.kotlin.psi.stubs.elements.KtConstantExpressionElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtNameReferenceExpressionElementType
import org.jetbrains.kotlin.utils.doNothing


class GrammarToTree(
    private val sourceIdentifier: SourceIdentifier,
    private val sourceCode: String,
    private val sourceOffset: Int,
    private val suffixLength: Int
) {

    inner
    class CachingLightTree(tree: LightTree) : LightTree by tree {
        private
        val sourceData: MutableMap<LighterASTNode, LightTreeSourceData> = mutableMapOf()

        fun sourceData(node: LighterASTNode, offset: Int = sourceOffset): LightTreeSourceData =
            sourceData.computeIfAbsent(node) {
                it.sourceData(sourceIdentifier, sourceCode, offset)
            }

        fun rootSourceData(): LightTreeSourceData =
            sourceData.computeIfAbsent(root) {
                LightTreeSourceData(sourceIdentifier, sourceCode, sourceOffset, sourceOffset..sourceCode.lastIndex - suffixLength)
            }

        fun parsingError(node: LighterASTNode, message: String): ParsingError {
            val sourceData = sourceData(node)
            return ParsingError(sourceData, sourceData, message)
        }

        fun parsingError(outer: LighterASTNode, inner: LighterASTNode, message: String): ParsingError {
            val outerSourceData = sourceData(outer)
            val innerSourceData = sourceData(inner)
            val cause = PsiBuilderImpl.getErrorMessage(inner)
            return ParsingError(outerSourceData, innerSourceData, "$message. $cause")
        }

        fun parsingError(outer: LighterASTNode, inner: LighterASTNode): ParsingError {
            val outerSourceData = sourceData(outer)
            val innerSourceData = sourceData(inner)
            val cause = PsiBuilderImpl.getErrorMessage(inner)
            return ParsingError(outerSourceData, innerSourceData, cause ?: "Unknown parsing error")
        }

        fun unsupported(node: LighterASTNode, feature: UnsupportedLanguageFeature): UnsupportedConstruct =
            unsupported(node, node, feature)

        fun unsupported(outer: LighterASTNode, inner: LighterASTNode, feature: UnsupportedLanguageFeature): UnsupportedConstruct {
            val outerSourceData = sourceData(outer)
            val innerSourceData = sourceData(inner)
            return UnsupportedConstruct(outerSourceData, innerSourceData, feature)
        }

        fun unsupportedNoOffset(outer: LighterASTNode, inner: LighterASTNode, feature: UnsupportedLanguageFeature): UnsupportedConstruct {
            val outerSourceData = sourceData(outer, 0)
            val innerSourceData = sourceData(inner, 0)
            return UnsupportedConstruct(outerSourceData, innerSourceData, feature)
        } // TODO: hack, due to script wrapping
    }

    fun script(originalTree: LightTree): LanguageTreeResult {
        val tree = CachingLightTree(originalTree)
        val packageNode = packageNode(tree)
        val importNodes = importNodes(tree)
        val scriptNodes = scriptNodes(tree)

        val packages = packageHeader(tree, packageNode)
        val imports = importNodes.map { import(tree, it) }
        val statements = scriptNodes.map { statement(tree, it) }

        val headerFailures = collectFailures(packages + imports)

        return LanguageTreeResult(
            imports = imports.filterIsInstance<Element<Import>>().map { it.element },
            topLevelBlock = Block(statements.map { it.asBlockElement() }, tree.rootSourceData()),
            headerFailures = headerFailures,
            codeFailures = collectFailures(statements)
        )
    }

    private
    fun packageHeader(tree: CachingLightTree, node: LighterASTNode): List<FailingResult> =
        when {
            tree.children(node).isNotEmpty() -> listOf(tree.unsupportedNoOffset(node, node, UnsupportedLanguageFeature.PackageHeader))
            else -> listOf()
        }

    private
    fun import(tree: CachingLightTree, node: LighterASTNode): ElementResult<Import> =
        elementOrFailure {
            val children = tree.children(node)

            var content: CheckedResult<ElementResult<PropertyAccess>>? = null
            children.forEach {
                when (it.tokenType) {
                    DOT_QUALIFIED_EXPRESSION, REFERENCE_EXPRESSION -> content = checkForFailure(propertyAccessStatement(tree, it))
                    MUL -> collectingFailure(tree.unsupportedNoOffset(node, it, UnsupportedLanguageFeature.StarImport))
                    IMPORT_ALIAS -> collectingFailure(tree.unsupportedNoOffset(node, it, UnsupportedLanguageFeature.RenamingImport))
                    ERROR_ELEMENT -> collectingFailure(tree.parsingError(node, it))
                }
            }

            collectingFailure(content ?: tree.parsingError(node, "Qualified expression without selector"))

            elementIfNoFailures {
                fun PropertyAccess.flatten(): List<String> =
                    buildList {
                        if (receiver is PropertyAccess) {
                            addAll(receiver.flatten())
                        }
                        add(name)
                    }

                val nameParts = checked(content!!).flatten()
                Element(Import(AccessChain(nameParts), tree.sourceData(node, offset = 0)))
            }
        }

    private
    fun statement(tree: CachingLightTree, node: LighterASTNode): ElementResult<DataStatement> =
        when (node.tokenType) {
            BINARY_EXPRESSION -> binaryStatement(tree, node)
            PROPERTY -> localValue(tree, node)
            else -> expression(tree, node)
        }

    private
    fun expression(tree: CachingLightTree, node: LighterASTNode): ElementResult<Expr> =
        when (val tokenType = node.tokenType) {
            BINARY_EXPRESSION -> binaryExpression(tree, node)
            LABELED_EXPRESSION -> tree.unsupported(node, UnsupportedLanguageFeature.LabelledStatement)
            ANNOTATED_EXPRESSION -> tree.unsupported(node, UnsupportedLanguageFeature.AnnotationUsage)
            in QUALIFIED_ACCESS, REFERENCE_EXPRESSION -> propertyAccessStatement(tree, node)
            is KtConstantExpressionElementType, INTEGER_LITERAL -> constantExpression(tree, node)
            STRING_TEMPLATE -> stringTemplate(tree, node)
            CALL_EXPRESSION -> callExpression(tree, node)
            in QUALIFIED_ACCESS -> qualifiedExpression(tree, node)
            CLASS, TYPEALIAS -> tree.unsupported(node, UnsupportedLanguageFeature.TypeDeclaration)
            ARRAY_ACCESS_EXPRESSION -> tree.unsupported(node, UnsupportedLanguageFeature.Indexing)
            FUN -> tree.unsupported(node, UnsupportedLanguageFeature.FunctionDeclaration)
            ERROR_ELEMENT -> tree.parsingError(node, node)
            PREFIX_EXPRESSION -> tree.unsupported(node, UnsupportedLanguageFeature.PrefixExpression)
            OPERATION_REFERENCE -> tree.unsupported(node, UnsupportedLanguageFeature.UnsupportedOperator)
            PARENTHESIZED -> parenthesized(tree, node)
            LAMBDA_EXPRESSION -> tree.unsupported(node, UnsupportedLanguageFeature.FunctionDeclaration)
            THIS_EXPRESSION -> Element(This(tree.sourceData(node)))
            else -> tree.parsingError(node, "Parsing failure, unexpected tokenType in expression: $tokenType")
        }

    private
    fun parenthesized(tree: CachingLightTree, node: LighterASTNode): ElementResult<Expr> =
        elementOrFailure {
            val children = childrenWithParsingErrorCollection(tree, node)

            val childExpression: LighterASTNode? = children.firstOrNull { it: LighterASTNode -> it.isExpression() }
            collectingFailure(childExpression ?: tree.parsingError(node, "No content in parenthesized expression"))

            elementIfNoFailures {
                expression(tree, childExpression!!)
            }
        }

    @Suppress("UNCHECKED_CAST")
    private
    fun propertyAccessStatement(tree: CachingLightTree, node: LighterASTNode): ElementResult<PropertyAccess> =
        when (val tokenType = node.tokenType) {
            REFERENCE_EXPRESSION -> Element(PropertyAccess(null, referenceExpression(node).value, tree.sourceData(node)))
            in QUALIFIED_ACCESS -> qualifiedExpression(tree, node) as ElementResult<PropertyAccess>
            ARRAY_ACCESS_EXPRESSION -> tree.unsupported(node, UnsupportedLanguageFeature.Indexing)
            else -> tree.parsingError(node, "Parsing failure, unexpected tokenType in property access statement: $tokenType")
        }

    private
    fun localValue(tree: CachingLightTree, node: LighterASTNode): ElementResult<LocalValue> =
        elementOrFailure {
            val children = tree.children(node)

            var identifier: Syntactic<String>? = null
            var expression: CheckedResult<ElementResult<Expr>>? = null
            children.forEach {
                when (val tokenType = it.tokenType) {
                    MODIFIER_LIST -> {
                        val modifiers = tree.children(it)
                        modifiers.forEach { modifier ->
                            when (modifier.tokenType) {
                                ANNOTATION_ENTRY -> collectingFailure(tree.unsupported(node, modifier, UnsupportedLanguageFeature.AnnotationUsage))
                                else -> collectingFailure(tree.unsupported(node, modifier, UnsupportedLanguageFeature.ValModifierNotSupported))
                            }
                        }
                    }
                    is KtSingleValueToken -> if (tokenType.value == "var") {
                        collectingFailure(tree.unsupported(node, it, UnsupportedLanguageFeature.LocalVarNotSupported))
                    }
                    IDENTIFIER -> identifier = Syntactic(it.asText)
                    COLON, TYPE_REFERENCE -> collectingFailure(tree.unsupported(node, it, UnsupportedLanguageFeature.ExplicitVariableType))
                    ERROR_ELEMENT -> collectingFailure(tree.parsingError(node, it))
                    else -> if (it.isExpression()) {
                        expression = checkForFailure(expression(tree, it))
                    }
                }
            }

            collectingFailure(identifier ?: tree.parsingError(node, "Local value without identifier"))
            collectingFailure(expression ?: tree.unsupported(node, UnsupportedLanguageFeature.UninitializedProperty))

            elementIfNoFailures {
                Element(LocalValue(identifier!!.value, checked(expression!!), tree.sourceData(node)))
            }
        }

    private
    fun qualifiedExpression(tree: CachingLightTree, node: LighterASTNode): ElementResult<Expr> =
        elementOrFailure {
            val children = tree.children(node)

            var isSelector = false
            var referenceSelector: CheckedResult<SyntacticResult<String>>? = null
            var referenceSourceData: SourceData? = null
            var functionCallSelector: CheckedResult<ElementResult<FunctionCall>>? = null
            var receiver: CheckedResult<ElementResult<Expr>>? = null // before dot
            children.forEach {
                when (val tokenType = it.tokenType) {
                    DOT -> isSelector = true
                    SAFE_ACCESS -> {
                        collectingFailure(tree.unsupported(node, it, UnsupportedLanguageFeature.SafeNavigation))
                    }
                    ERROR_ELEMENT -> collectingFailure(tree.parsingError(node, it))
                    else -> {
                        val isEffectiveSelector = isSelector && tokenType != ERROR_ELEMENT
                        if (isEffectiveSelector) {
                            val callExpressionCallee = if (tokenType == CALL_EXPRESSION) tree.getFirstChildExpressionUnwrapped(it) else null
                            if (tokenType is KtNameReferenceExpressionElementType) {
                                referenceSelector = checkForFailure(referenceExpression(it))
                                referenceSourceData = tree.sourceData(it)
                            } else if (tokenType == CALL_EXPRESSION && callExpressionCallee?.tokenType != LAMBDA_EXPRESSION) {
                                functionCallSelector = checkForFailure(callExpression(tree, it))
                            } else {
                                collectingFailure(tree.parsingError(node, it, "The expression cannot be a selector (occur after a dot)"))
                            }
                        } else {
                            receiver = checkForFailure(expression(tree, it))
                        }
                    }
                }
            }

            collectingFailure(referenceSelector ?: functionCallSelector ?: tree.parsingError(node, "Qualified expression without selector"))
            collectingFailure(receiver ?: tree.parsingError(node, "Qualified expression without receiver"))

            elementIfNoFailures {
                if (referenceSelector != null) {
                    Element(PropertyAccess(checked(receiver!!), checked(referenceSelector!!), referenceSourceData!!))
                } else {
                    val functionCall = checked(functionCallSelector!!)
                    Element(FunctionCall(checked(receiver!!), functionCall.name, functionCall.args, functionCall.sourceData))
                }
            }
        }

    private
    fun stringTemplate(tree: CachingLightTree, node: LighterASTNode): ElementResult<Expr> {
        val children = tree.children(node)
        val sb = StringBuilder()
        children.forEach {
            when (val tokenType = it.tokenType) {
                OPEN_QUOTE, CLOSING_QUOTE -> {}
                LITERAL_STRING_TEMPLATE_ENTRY -> sb.append(it.asText)
                ERROR_ELEMENT -> tree.parsingError(node, it, "Unparsable string template: \"${node.asText}\"")
                else -> tree.parsingError(it, "Parsing failure, unexpected tokenType in string template: $tokenType")
            }
        }
        return Element(Literal.StringLiteral(sb.toString(), tree.sourceData(node)))
    }

    private
    fun constantExpression(tree: CachingLightTree, node: LighterASTNode): ElementResult<Expr> {
        val type = node.tokenType
        val text: String = node.asText

        fun reportIncorrectConstant(cause: String): ParsingError =
            tree.parsingError(node, "Incorrect constant expression, $cause: ${node.asText}")


        val convertedText: Any? = when (type) {
            INTEGER_CONSTANT, INTEGER_LITERAL -> when {
                hasIllegalUnderscore(text, type) -> return reportIncorrectConstant("illegal underscore")
                else -> parseNumericLiteral(text, INTEGER_CONSTANT)
            }
            BOOLEAN_CONSTANT -> parseBoolean(text)
            else -> null
        }

        when (type) {
            INTEGER_CONSTANT, INTEGER_LITERAL -> {
                when {
                    convertedText == null -> {
                        return reportIncorrectConstant("missing value")
                    }

                    convertedText !is Long -> return reportIncorrectConstant("illegal constant expression")

                    hasUnsignedLongSuffix(text) || hasLongSuffix(text) -> {
                        if (text.endsWith("l")) {
                            return reportIncorrectConstant("wrong long suffix")
                        }
                        return Element(Literal.LongLiteral(convertedText, tree.sourceData(node)))
                    }

                    else -> {
                        return Element(Literal.IntLiteral(convertedText.toInt(), tree.sourceData(node)))
                    }
                }
            }
            BOOLEAN_CONSTANT -> return Element(Literal.BooleanLiteral(convertedText as Boolean, tree.sourceData(node)))
            NULL -> return Element(Null(tree.sourceData(node)))
            else -> return tree.parsingError(node, "Parsing failure, unsupported constant type: $type")
        }
    }

    private
    fun callExpression(tree: CachingLightTree, node: LighterASTNode): ElementResult<FunctionCall> {
        val children = tree.children(node)

        var name: String? = null
        val valueArguments = mutableListOf<LighterASTNode>()
        children.forEach { child ->
            fun process(node: LighterASTNode) {
                when (val tokenType = node.tokenType) {
                    REFERENCE_EXPRESSION -> {
                        name = node.asText
                    }
                    VALUE_ARGUMENT_LIST, LAMBDA_ARGUMENT -> {
                        valueArguments += node
                    }
                    else -> tree.parsingError(node, "Parsing failure, unexpected token type in call expression: $tokenType")
                }
            }

            process(child)
        }

        if (name == null) tree.parsingError(node, "Name missing from function call!")

        return elementOrFailure {
            val arguments = valueArguments.flatMap { valueArguments(tree, it) }.map { checkForFailure(it) }
            elementIfNoFailures {
                Element(FunctionCall(null, name!!, arguments.map(::checked), tree.sourceData(node)))
            }
        }
    }

    private
    fun valueArguments(tree: CachingLightTree, node: LighterASTNode): List<SyntacticResult<FunctionArgument>> {
        val children = tree.children(node)

        val list = mutableListOf<SyntacticResult<FunctionArgument>>()
        children.forEach {
            when (val tokenType = it.tokenType) {
                VALUE_ARGUMENT -> list.add(valueArgument(tree, it))
                COMMA, LPAR, RPAR -> doNothing()
                LAMBDA_EXPRESSION -> list.add(lambda(tree, it))
                ERROR_ELEMENT -> list.add(tree.parsingError(node, it, "Unparsable value argument: \"${node.asText}\""))
                else -> tree.parsingError(it, "Parsing failure, unexpected token type in value arguments: $tokenType")
            }
        }
        return list
    }

    private
    fun lambda(tree: CachingLightTree, node: LighterASTNode): SyntacticResult<FunctionArgument.Lambda> =
        syntacticOrFailure {
            val children = childrenWithParsingErrorCollection(tree, node)
            val functionalLiteralNode = children.firstOrNull { it: LighterASTNode -> it.isKind(FUNCTION_LITERAL) }

            collectingFailure(functionalLiteralNode ?: tree.parsingError(node, "No functional literal in lambda definition"))

            var block: LighterASTNode? = null
            functionalLiteralNode?.let {
                val literalNodeChildren = tree.children(functionalLiteralNode)
                literalNodeChildren.forEach {
                    when (it.tokenType) {
                        VALUE_PARAMETER_LIST -> collectingFailure(tree.unsupported(node, it, UnsupportedLanguageFeature.LambdaWithParameters))
                        BLOCK -> block = it
                        ARROW -> doNothing()
                    }
                }
            }

            var statements: List<ElementResult<DataStatement>>? = null
            block?.let {
                statements = tree.children(block!!).map { statement(tree, it) }
            }

            collectingFailure(statements ?: tree.parsingError(node, "Lambda expression without statements"))

            syntacticIfNoFailures {
                val checkedStatements = statements!!.map { it.asBlockElement() }
                val b = Block(checkedStatements, tree.sourceData(block!!))
                Syntactic(FunctionArgument.Lambda(b, tree.sourceData(node)))
            }
        }


    private
    fun valueArgument(tree: CachingLightTree, node: LighterASTNode): SyntacticResult<FunctionArgument.ValueArgument> =
        syntacticOrFailure {
            if (node.tokenType == PARENTHESIZED) return@syntacticOrFailure valueArgument(tree, tree.getFirstChildExpressionUnwrapped(node)!!)

            var expression: CheckedResult<ElementResult<Expr>>? = null

            when (node.tokenType) {
                INTEGER_LITERAL, INTEGER_CONSTANT, BOOLEAN_CONSTANT -> expression = checkForFailure(constantExpression(tree, node))
                STRING_TEMPLATE -> expression = checkForFailure(stringTemplate(tree, node))
                DOT_QUALIFIED_EXPRESSION -> expression = checkForFailure(propertyAccessStatement(tree, node))
            }

            if (expression != null) {
                return@syntacticOrFailure syntacticIfNoFailures {
                    Syntactic(FunctionArgument.Positional(checked(expression!!), tree.sourceData(node)))
                }
            }

            val children = tree.children(node)
            var identifier: String? = null
            children.forEach {
                when (val tokenType = it.tokenType) {
                    VALUE_ARGUMENT_NAME -> identifier = it.asText
                    EQ -> doNothing()
                    is KtConstantExpressionElementType -> expression = checkForFailure(constantExpression(tree, it))
                    CALL_EXPRESSION -> expression = checkForFailure(callExpression(tree, it))
                    else ->
                        if (it.isExpression()) expression = checkForFailure(expression(tree, it))
                        else tree.parsingError(it, "Parsing failure, unexpected token type in value argument: $tokenType")
                }
            }

            collectingFailure(expression ?: tree.parsingError(node, "Argument is absent"))

            syntacticIfNoFailures {
                Syntactic(
                    if (identifier != null) FunctionArgument.Named(identifier!!, checked(expression!!), tree.sourceData(node))
                    else FunctionArgument.Positional(checked(expression!!), tree.sourceData(node))
                )
            }
        }

    @Suppress("UNCHECKED_CAST")
    private
    fun binaryExpression(tree: CachingLightTree, node: LighterASTNode): ElementResult<Expr> =
        when (val binaryStatement = binaryStatement(tree, node)) {
            is FailingResult -> binaryStatement
            is Element -> if (binaryStatement.element is Expr) binaryStatement as ElementResult<Expr>
            else tree.unsupported(node, UnsupportedLanguageFeature.UnsupportedOperationInBinaryExpression)
        }

    private
    fun binaryStatement(tree: CachingLightTree, node: LighterASTNode): ElementResult<DataStatement> {
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

        if (leftArg == null) return tree.parsingError(node, "Missing left hand side in binary expression")
        if (rightArg == null) return tree.parsingError(node, "Missing right hand side in binary expression")

        return when (operationToken) {
            EQ -> elementOrFailure {
                val lhs = checkForFailure(propertyAccessStatement(tree, leftArg!!))
                val expr = checkForFailure(expression(tree, rightArg!!))

                elementIfNoFailures {
                    Element(Assignment(checked(lhs), checked(expr), tree.sourceData(node)))
                }
            }

            IDENTIFIER -> elementOrFailure {
                val receiver = checkForFailure(expression(tree, leftArg!!))
                val argument = checkForFailure(valueArgument(tree, rightArg!!))
                elementIfNoFailures {
                    Element(FunctionCall(checked(receiver), operationTokenName, listOf(checked(argument)), tree.sourceData(node)))
                }
            }

            else -> tree.unsupported(node, UnsupportedLanguageFeature.UnsupportedOperationInBinaryExpression)
        }
    }

    private
    fun referenceExpression(node: LighterASTNode): Syntactic<String> = Syntactic(node.asText)

    private
    fun packageNode(tree: LightTree): LighterASTNode =
        toplevelNode(tree, PACKAGE_DIRECTIVE)

    private
    fun importNodes(tree: LightTree): List<LighterASTNode> =
        tree.children(toplevelNode(tree, IMPORT_LIST))

    private
    fun scriptNodes(tree: LightTree): List<LighterASTNode> {
        // the actual script we want to parse is wrapped into a class initializer block, we need to extract it

        val childrenOfWrappingClass = tree.children(toplevelNode(tree, CLASS))
        val wrappingClassBody = childrenOfWrappingClass.expectSingleOfKind(CLASS_BODY)
        val childrenOfWrappingClassBody = tree.children(wrappingClassBody)
        val wrappingClassInitializer = childrenOfWrappingClassBody.expectSingleOfKind(CLASS_INITIALIZER)
        val childrenOfWrappingClassInitializer = tree.children(wrappingClassInitializer)
        val wrappingClassInitializerBlock = childrenOfWrappingClassInitializer.expectSingleOfKind(BLOCK)
        val childrenOfWrappingClassInitializerBlock = tree.children(wrappingClassInitializerBlock)

        return extractBlockContent(childrenOfWrappingClassInitializerBlock)
    }

    private
    fun toplevelNode(tree: LightTree, parentNode: IElementType): LighterASTNode {
        val root = tree.root
        val childrenOfRoot = tree.children(root)
        return childrenOfRoot.expectSingleOfKind(parentNode)
    }

    private
    fun extractBlockContent(blockNodes: List<LighterASTNode>): List<LighterASTNode> {
        check(blockNodes.size >= 2) // first and last nodes are the opening an¡¡d closing braces

        val openBrace = blockNodes.first()
        openBrace.expectKind(LBRACE)

        val closingBrace = blockNodes.last()
        closingBrace.expectKind(RBRACE)

        return blockNodes.slice(1..blockNodes.size - 2)
    }

    private
    fun FailureCollectorContext.childrenWithParsingErrorCollection(tree: CachingLightTree, node: LighterASTNode): List<LighterASTNode> {
        val children = tree.children(node)
        children.forEach {
            if (it.tokenType == ERROR_ELEMENT) {
                collectingFailure(tree.parsingError(node, it))
            }
        }
        return children
    }
}
