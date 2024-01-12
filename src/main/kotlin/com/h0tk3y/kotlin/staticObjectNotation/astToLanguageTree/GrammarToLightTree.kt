package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.FailureCollectorContext.CheckedResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedLanguageFeature.*
import com.h0tk3y.kotlin.staticObjectNotation.language.*
import org.jetbrains.kotlin.ElementTypeUtils.getOperationSymbol
import org.jetbrains.kotlin.ElementTypeUtils.isExpression
import org.jetbrains.kotlin.KtNodeTypes.*
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.lang.impl.PsiBuilderImpl
import org.jetbrains.kotlin.com.intellij.psi.TokenType.ERROR_ELEMENT
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.parsing.*
import org.jetbrains.kotlin.psi.stubs.elements.KtConstantExpressionElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtNameReferenceExpressionElementType
import org.jetbrains.kotlin.utils.doNothing

class GrammarToLightTree(
    private val sourceIdentifier: SourceIdentifier,
    private val sourceCode: String,
    private val sourceOffset: Int
) {

    fun script(tree: LightTree): Syntactic<List<ElementResult<*>>> {
        val packageNode = packageNode(tree)
        val importNodes = importNodes(tree)
        val scriptNodes = scriptNodes(tree)

        val packages = packageHeader(tree, packageNode)
        val imports = importNodes.map { import(tree, it) }
        val statements = scriptNodes.map { statement(tree, it) }
            .flatMap { surfaceInternalFailures(it) }
        return Syntactic(packages + imports + statements)
    }

    private
    fun surfaceInternalFailures(elementOrFailure: ElementResult<DataStatement>): List<ElementResult<*>> {
        fun MutableList<ElementResult<*>>.recurse(current: LanguageTreeElement): MutableList<ElementResult<*>> {
            when (current) {
                is Block -> {
                    current.content.forEach {
                        when (it) {
                            is ErroneousStatement -> add(it.failingResult)
                            else -> recurse(it)
                        }
                    }
                }

                is Assignment -> {
                    recurse(current.lhs)
                    recurse(current.rhs)
                }

                is FunctionCall -> {
                    current.receiver?.let {
                        recurse(it)
                    }
                    if (current.args.isNotEmpty()) {
                        current.args.forEach {
                            recurse(it)
                        }
                    }
                }

                is PropertyAccess -> {
                    current.receiver?.let { receiver ->
                        recurse(receiver)
                    }
                }

                is LocalValue -> recurse(current.rhs)

                is FunctionArgument.Lambda -> recurse(current.block)
                is FunctionArgument.Named -> recurse(current.expr)
                is FunctionArgument.Positional -> recurse(current.expr)

                is Import ->  doNothing()

                is Literal.BooleanLiteral -> doNothing()
                is Literal.IntLiteral -> doNothing()
                is Literal.LongLiteral -> doNothing()
                is Literal.StringLiteral -> doNothing()
                is Null -> doNothing()
                is This -> doNothing()

                else -> error("Unhandled languege tree element: ${current.javaClass.simpleName}")
            }

            return this
        }

        val results = mutableListOf<ElementResult<*>>()
        if (elementOrFailure is Element) {
            results.recurse(elementOrFailure.element)
        }
        results.add(elementOrFailure)
        return results
    }

    private
    fun packageHeader(tree: LightTree, node: LighterASTNode): List<FailingResult> =
        when {
            tree.children(node).isNotEmpty() -> listOf(node.unsupportedNoOffset(PackageHeader))
            else -> listOf()
        }

    private
    fun import(tree: LightTree, node: LighterASTNode): ElementResult<Import> =
        elementOrFailure {
            val children = tree.children(node)

            var content: CheckedResult<ElementResult<PropertyAccess>>? = null
            children.forEach {
                when (it.tokenType) {
                    DOT_QUALIFIED_EXPRESSION, REFERENCE_EXPRESSION -> content = checkForFailure(propertyAccessStatement(tree, it))
                    MUL -> collectingFailure(it.unsupportedInNoOffset(node, StarImport))
                    IMPORT_ALIAS -> collectingFailure(it.unsupportedInNoOffset(node, RenamingImport))
                    ERROR_ELEMENT -> collectingFailure(it.parsingError(node))
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
                Element(Import(AccessChain(nameParts), node.dataNoOffset))
            }
        }

    private
    fun statement(tree: LightTree, node: LighterASTNode): ElementResult<DataStatement> =
        when (node.tokenType) {
            BINARY_EXPRESSION -> binaryStatement(tree, node)
            PROPERTY -> localValue(tree, node)
            else -> expression(tree, node)
        }

    private
    fun expression(tree: LightTree, node: LighterASTNode): ElementResult<Expr> =
        when (val tokenType = node.tokenType) {
            BINARY_EXPRESSION -> binaryExpression(tree, node)
            LABELED_EXPRESSION -> node.unsupported(LabelledStatement)
            ANNOTATED_EXPRESSION -> node.unsupported(AnnotationUsage)
            in QUALIFIED_ACCESS, REFERENCE_EXPRESSION -> propertyAccessStatement(tree, node)
            is KtConstantExpressionElementType, INTEGER_LITERAL -> constantExpression(node)
            STRING_TEMPLATE -> stringTemplate(tree, node)
            CALL_EXPRESSION -> callExpression(tree, node)
            in QUALIFIED_ACCESS -> qualifiedExpression(tree, node)
            CLASS, TYPEALIAS -> node.unsupported(TypeDeclaration)
            ARRAY_ACCESS_EXPRESSION -> node.unsupported(Indexing)
            FUN -> node.unsupported(FunctionDeclaration)
            ERROR_ELEMENT -> node.parsingError(node)
            PREFIX_EXPRESSION -> node.unsupported(PrefixExpression)
            OPERATION_REFERENCE -> node.unsupported(UnsupportedOperator)
            PARENTHESIZED -> parenthesized(tree, node)
            LAMBDA_EXPRESSION -> node.unsupported(FunctionDeclaration)
            THIS_EXPRESSION -> Element(This(node.data))
            else -> node.parsingError("Parsing failure, unexpected tokenType in expression: $tokenType")
        }

    private
    fun parenthesized(tree: LightTree, node: LighterASTNode): ElementResult<Expr> =
        elementOrFailure {
            val children = childrenWithParsingErrorCollection(tree, node)

            val childExpression: LighterASTNode? = children.firstOrNull { it: LighterASTNode -> it.isExpression() }
            collectingFailure(childExpression ?: node.parsingError("No content in parenthesized expression"))

            elementIfNoFailures {
                expression(tree, childExpression!!)
            }
        }

    @Suppress("UNCHECKED_CAST")
    private
    fun propertyAccessStatement(tree: LightTree, node: LighterASTNode): ElementResult<PropertyAccess> =
        when (val tokenType = node.tokenType) {
            REFERENCE_EXPRESSION -> Element(PropertyAccess(null, referenceExpression(node).value, node.data))
            in QUALIFIED_ACCESS -> qualifiedExpression(tree, node) as ElementResult<PropertyAccess>
            ARRAY_ACCESS_EXPRESSION -> node.unsupported(Indexing)
            else -> node.parsingError("Parsing failure, unexpected tokenType in property access statement: $tokenType")
        }

    private
    fun localValue(tree: LightTree, node: LighterASTNode): ElementResult<LocalValue> =
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
                                ANNOTATION_ENTRY -> collectingFailure(modifier.unsupportedIn(node, AnnotationUsage))
                                else -> collectingFailure(modifier.unsupportedIn(node, ValModifierNotSupported))
                            }
                        }
                    }
                    is KtSingleValueToken -> if (tokenType.value == "var") {
                        collectingFailure(it.unsupportedIn(node, LocalVarNotSupported))
                    }
                    IDENTIFIER -> identifier = Syntactic(it.asText)
                    COLON, TYPE_REFERENCE -> collectingFailure(it.unsupportedIn(node, ExplicitVariableType))
                    ERROR_ELEMENT -> collectingFailure(it.parsingError(node))
                    else -> if (it.isExpression()) {
                        expression = checkForFailure(expression(tree, it))
                    }
                }
            }

            collectingFailure(identifier ?: node.parsingError("Local value without identifier"))
            collectingFailure(expression ?: node.unsupported(UninitializedProperty))

            elementIfNoFailures {
                Element(LocalValue(identifier!!.value, checked(expression!!), node.data))
            }
        }

    private
    fun qualifiedExpression(tree: LightTree, node: LighterASTNode): ElementResult<Expr> =
        elementOrFailure {
            val children = tree.children(node)

            var isSelector = false
            var referenceSelector: CheckedResult<SyntacticResult<String>>? = null
            var referenceSourceData: SourceData? = null
            var functionCallSelector: CheckedResult<ElementResult<FunctionCall>>? = null
            var receiver: CheckedResult<ElementResult<Expr>>? = null //before dot
            children.forEach {
                when (val tokenType = it.tokenType) {
                    DOT -> isSelector = true
                    SAFE_ACCESS -> {
                        collectingFailure(it.unsupportedIn(node, SafeNavigation))
                    }
                    ERROR_ELEMENT -> collectingFailure(it.parsingError(node))
                    else -> {
                        val isEffectiveSelector = isSelector && tokenType != ERROR_ELEMENT
                        if (isEffectiveSelector) {
                            val callExpressionCallee = if (tokenType == CALL_EXPRESSION) tree.getFirstChildExpressionUnwrapped(it) else null
                            if (tokenType is KtNameReferenceExpressionElementType) {
                                referenceSelector = checkForFailure(referenceExpression(it))
                                referenceSourceData = it.data
                            } else if (tokenType == CALL_EXPRESSION && callExpressionCallee?.tokenType != LAMBDA_EXPRESSION) {
                                functionCallSelector = checkForFailure(callExpression(tree, it))
                            } else {
                                collectingFailure(it.parsingError(node, "The expression cannot be a selector (occur after a dot)"))
                            }
                        } else {
                            receiver = checkForFailure(expression(tree, it))
                        }
                    }
                }
            }

            collectingFailure(referenceSelector ?: functionCallSelector ?: node.parsingError("Qualified expression without selector"))
            collectingFailure(receiver ?: node.parsingError("Qualified expression without receiver"))

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
    fun stringTemplate(tree: LightTree, node: LighterASTNode): ElementResult<Expr> {
        val children = tree.children(node)
        val sb = StringBuilder()
        children.forEach {
            when (val tokenType = it.tokenType) {
                OPEN_QUOTE, CLOSING_QUOTE -> {}
                LITERAL_STRING_TEMPLATE_ENTRY -> sb.append(it.asText)
                ERROR_ELEMENT -> it.parsingError(node, "Unparsable string template: \"${node.asText}\"")
                else -> it.parsingError("Parsing failure, unexpected tokenType in string template: $tokenType")
            }
        }
        return Element(Literal.StringLiteral(sb.toString(), node.data))
    }

    private
    fun constantExpression(node: LighterASTNode): ElementResult<Expr> {
        val type = node.tokenType
        val text: String = node.asText

        fun reportIncorrectConstant(cause: String): ParsingError =
            node.parsingError("Incorrect constant expression, $cause: ${node.asText}")


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
                        return Element(Literal.LongLiteral(convertedText, node.data))
                    }

                    else -> {
                        return Element(Literal.IntLiteral(convertedText.toInt(), node.data))
                    }
                }
            }
            BOOLEAN_CONSTANT -> return Element(Literal.BooleanLiteral(convertedText as Boolean, node.data))
            NULL -> return Element(Null(node.data))
            else -> return node.parsingError("Parsing failure, unsupported constant type: $type")
        }
    }

    private
    fun callExpression(tree: LightTree, node: LighterASTNode): ElementResult<FunctionCall> {
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
                    else -> node.parsingError("Parsing failure, unexpected token type in call expression: $tokenType")
                }
            }

            process(child)
        }

        if (name == null) node.parsingError("Name missing from function call!")

        return elementOrFailure {
            val arguments = valueArguments.flatMap { valueArguments(tree, it) }.map { checkForFailure(it) }
            elementIfNoFailures {
                Element(FunctionCall(null, name!!, arguments.map(::checked), node.data))
            }
        }
    }

    private
    fun valueArguments(tree: LightTree, node: LighterASTNode): List<SyntacticResult<FunctionArgument>> {
        val children = tree.children(node)

        val list = mutableListOf<SyntacticResult<FunctionArgument>>()
        children.forEach {
            when (val tokenType = it.tokenType) {
                VALUE_ARGUMENT -> list.add(valueArgument(tree, it))
                COMMA, LPAR, RPAR -> doNothing()
                LAMBDA_EXPRESSION -> list.add(lambda(tree, it))
                ERROR_ELEMENT -> list.add(it.parsingError(node, "Unparsable value argument: \"${node.asText}\""))
                else -> it.parsingError("Parsing failure, unexpected token type in value arguments: $tokenType")
            }
        }
        return list
    }

    private
    fun lambda(tree: LightTree, node: LighterASTNode): SyntacticResult<FunctionArgument.Lambda> =
        syntacticOrFailure {
            val children = childrenWithParsingErrorCollection(tree, node)
            val functionalLiteralNode = children.firstOrNull { it: LighterASTNode -> it.isKind(FUNCTION_LITERAL) }

            collectingFailure(functionalLiteralNode ?: node.parsingError("No functional literal in lambda definition"))

            var block: LighterASTNode? = null
            functionalLiteralNode?.let {
                val literalNodeChildren = tree.children(functionalLiteralNode)
                literalNodeChildren.forEach {
                    when (it.tokenType) {
                        VALUE_PARAMETER_LIST -> collectingFailure(it.unsupportedIn(node, LambdaWithParameters))
                        BLOCK -> block = it
                        ARROW -> doNothing()
                    }
                }
            }

            var statements: List<ElementResult<DataStatement>>? = null
            block?.let {
                statements = tree.children(block!!).map { statement(tree, it) }
            }

            collectingFailure(statements ?: node.parsingError("Lambda expression without statements"))

            syntacticIfNoFailures {
                val checkedStatements = statements!!.map {
                    when (it) {
                        is Element -> it.element
                        is FailingResult -> ErroneousStatement(it)
                    }
                }
                val b = Block(checkedStatements, block!!.data)
                Syntactic(FunctionArgument.Lambda(b, node.data))
            }
        }

    private
    fun valueArgument(tree: LightTree, node: LighterASTNode): SyntacticResult<FunctionArgument.ValueArgument> =
        syntacticOrFailure {
            if (node.tokenType == PARENTHESIZED) return@syntacticOrFailure valueArgument(tree, tree.getFirstChildExpressionUnwrapped(node)!!)

            var expression: CheckedResult<ElementResult<Expr>>? = null

            when (node.tokenType) {
                INTEGER_LITERAL, INTEGER_CONSTANT, BOOLEAN_CONSTANT -> expression = checkForFailure(constantExpression(node))
                STRING_TEMPLATE -> expression = checkForFailure(stringTemplate(tree, node))
                DOT_QUALIFIED_EXPRESSION -> expression = checkForFailure(propertyAccessStatement(tree, node))
            }

            if (expression != null) {
                return@syntacticOrFailure syntacticIfNoFailures {
                    Syntactic(FunctionArgument.Positional(checked(expression!!), node.data))
                }
            }

            val children = tree.children(node)
            var identifier: String? = null
            children.forEach {
                when (val tokenType = it.tokenType) {
                    VALUE_ARGUMENT_NAME -> identifier = it.asText
                    EQ -> doNothing()
                    is KtConstantExpressionElementType -> expression = checkForFailure(constantExpression(it))
                    CALL_EXPRESSION -> expression = checkForFailure(callExpression(tree, it))
                    else ->
                        if (it.isExpression()) expression = checkForFailure(expression(tree, it))
                        else it.parsingError("Parsing failure, unexpected token type in value argument: $tokenType")
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

    @Suppress("UNCHECKED_CAST")
    private
    fun binaryExpression(tree: LightTree, node: LighterASTNode): ElementResult<Expr> =
        when (val binaryStatement = binaryStatement(tree, node)) {
            is FailingResult -> binaryStatement
            is Element -> if (binaryStatement.element is Expr) binaryStatement as ElementResult<Expr>
            else node.unsupported(UnsupportedOperationInBinaryExpression)
        }

    private
    fun binaryStatement(tree: LightTree, node: LighterASTNode): ElementResult<DataStatement> {
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

        if (leftArg == null) return node.parsingError("Missing left hand side in binary expression")
        if (rightArg == null) return node.parsingError("Missing right hand side in binary expression")

        return when (operationToken) {
            EQ -> elementOrFailure {
                val lhs = checkForFailure(propertyAccessStatement(tree, leftArg!!))
                val expr = checkForFailure(expression(tree, rightArg!!))

                elementIfNoFailures {
                    Element(Assignment(checked(lhs), checked(expr), node.data))
                }
            }

            IDENTIFIER -> elementOrFailure {
                val receiver = checkForFailure(expression(tree, leftArg!!))
                val argument = checkForFailure(valueArgument(tree, rightArg!!))
                elementIfNoFailures {
                    Element(FunctionCall(checked(receiver), operationTokenName, listOf(checked(argument)), node.data))
                }
            }

            else -> node.unsupported(UnsupportedOperationInBinaryExpression)
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
    val LighterASTNode.data get() = sourceData(sourceIdentifier, sourceCode, sourceOffset)
    private
    val LighterASTNode.dataNoOffset get() = sourceData(sourceIdentifier, sourceCode, 0) // TODO: again a hack, due to script wrapping

    private
    fun LighterASTNode.unsupported(feature: UnsupportedLanguageFeature) =
        UnsupportedConstruct(this.data, this.data, feature)

    private
    fun LighterASTNode.unsupportedNoOffset(feature: UnsupportedLanguageFeature) = // TODO: again a hack, due to script wrapping
        UnsupportedConstruct(this.dataNoOffset, this.dataNoOffset, feature)

    private
    fun LighterASTNode.unsupportedIn(
        outer: LighterASTNode,
        feature: UnsupportedLanguageFeature
    ) = UnsupportedConstruct(outer.data, this.data, feature)

    private
    fun LighterASTNode.unsupportedInNoOffset( // TODO: again a hack, due to script wrapping
        outer: LighterASTNode,
        feature: UnsupportedLanguageFeature
    ) = UnsupportedConstruct(outer.dataNoOffset, this.dataNoOffset, feature)

    private
    fun FailureCollectorContext.childrenWithParsingErrorCollection(tree: LightTree, node: LighterASTNode): List<LighterASTNode> {
        val children = tree.children(node)
        children.forEach {
            if (it.tokenType == ERROR_ELEMENT) {
                collectingFailure(it.parsingError(node))
            }
        }
        return children
    }

    private
    fun LighterASTNode.parsingError(outer: LighterASTNode): ParsingError {
        val cause = PsiBuilderImpl.getErrorMessage(this)
        return ParsingError(outer.data, this.data, cause ?: "Unknown parsing error")
    }

    private
    fun LighterASTNode.parsingError(outer: LighterASTNode, message: String): ParsingError {
        val cause = PsiBuilderImpl.getErrorMessage(this)
        return ParsingError(outer.data, this.data, "$message. $cause")
    }

    private
    fun LighterASTNode.parsingError(message: String): ParsingError =
        ParsingError(this.data, this.data, message)

}

