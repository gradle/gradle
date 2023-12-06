package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.AstKind.*
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedLanguageFeature.*
import com.h0tk3y.kotlin.staticObjectNotation.language.*
import kotlinx.ast.common.ast.Ast

class GrammarToTree(private val sourceIdentifier: SourceIdentifier) {
    /**
     * script : -shebangLine? -fileAnnotation* -packageHeader importList (statement semi)* EOF
     */
    fun script(ast: Ast): Syntactic<List<ElementResult<*>>> =
        FailureCollectorContext().run {
            ast.expectKind(script)
            collectingFailure(ast, shebangLine) { it?.unsupported(UnsupportedShebangInScript) }
            collectingFailure(ast, packageHeader) {
                it?.takeIf { it.childrenOrEmpty.isNotEmpty() }?.unsupported(PackageHeader)
            }

            val imports = importList(ast.child(importList))

            val statementAsts = ast.children(statement)
            val statements = statementAsts.map { statement(it) }

            Syntactic(failures + imports + statements)
        }

    private fun importList(ast: Ast): List<ElementResult<Import>> {
        ast.expectKind(importList)
        return ast.children(importHeader).map { importHeader(it) }
    }

    private fun importHeader(ast: Ast): ElementResult<Import> =
        elementOrFailure {
            ast.expectKind(importHeader)

            collectingFailure(ast, asterisk) { it?.unsupported(StarImport) }
            collectingFailure(ast, importAlias) { it?.unsupported(RenamingImport) }

            val identifierAst = ast.child(identifier)

            val names = identifierAst.children(simpleIdentifier).map { checkForFailure(simpleIdentifier(it)) }

            elementIfNoFailures {
                Element(Import(AccessChain(checked(names), identifierAst.data), ast.data))
            }
        }

    /**
     * statement : (-label | -annotation)* ([declaration] | [assignment] | -loopStatement | [expression])
     */
    private fun statement(ast: Ast): ElementResult<DataStatement> =
        elementOrFailure {
            ast.expectKind(statement)

            collectingFailure(ast, label) { it?.let { ast.unsupportedBecause(it, LabelledStatement) } }
            collectingFailure(ast, annotation) { it?.let { ast.unsupportedBecause(it, AnnotationUsage) } }

            val singleChild =
                ast.childrenOrEmpty.singleOrNull { it.kind != label && it.kind != annotation && it.kind != whitespace }
                    ?: error("expected a single child")

            elementIfNoFailures {
                when (singleChild.kind) {
                    loopStatement -> singleChild.unsupported(LoopStatement)
                    declaration -> declaration(singleChild)
                    assignment -> assignment(singleChild)
                    expression -> expression(singleChild)
                    else -> error("unexpected child kind")
                }
            }
        }

    /**
     * declaration : -classDeclaration | -objectDeclaration | [functionDeclaration] | [propertyDeclaration] | -typeAlias
     */
    private fun declaration(ast: Ast): ElementResult<DataStatement> {
        ast.expectKind(declaration)

        val singleChild = ast.singleChild()
        return when (singleChild.kind) {
            classDeclaration, objectDeclaration, typeAlias -> ast.unsupportedBecause(singleChild, TypeDeclaration)
            functionDeclaration -> functionDeclaration(singleChild)
            propertyDeclaration -> propertyDeclaration(singleChild)
            else -> error("unexpected child kind")
        }
    }

    /**
     * propertyDeclaration : -modifiers? ('val' | -'var') -typeParameters?
     *     -(receiverType '.')?
     *     (-multiVariableDeclaration | [variableDeclaration])
     *     -typeConstraints?
     *     (('=' expression) | -propertyDelegate)? ';'?
     *     ((getter? (semi? setter)?) | (setter? (semi? getter)?))
     */
    private fun propertyDeclaration(ast: Ast): ElementResult<LocalValue> =
        elementOrFailure {
            ast.expectKind(propertyDeclaration)

            collectingFailure(ast, modifiers) { it?.let { ast.unsupportedBecause(it, ValModifierNotSupported) } }
            collectingFailure(ast, varKeyword) { it?.let { ast.unsupportedBecause(it, LocalVarNotSupported) } }
            collectingFailure(ast, receiverType) { it?.let { ast.unsupportedBecause(it, ExtensionProperty) } }
            collectingFailure(ast, multiVariableDeclaration) { it?.let { ast.unsupportedBecause(it, MultiVariable) } }
            collectingFailure(ast, typeConstraints) { it?.let { ast.unsupportedBecause(it, GenericDeclaration) } }
            collectingFailure(ast, propertyDelegate) { it?.let { ast.unsupportedBecause(it, DelegatedProperty) } }
            collectingFailure(ast, getter) { it?.let { ast.unsupportedBecause(it, CustomAccessor) } }
            collectingFailure(ast, setter) { it?.let { ast.unsupportedBecause(it, CustomAccessor) } }

            val name = checkForFailure(variableDeclaration(ast.child(variableDeclaration)))
            val expr = checkForFailure(
                ast.findChild(expression)?.let(::expression) ?: ast.unsupported(UninitializedProperty)
            )

            elementIfNoFailures {
                Element(LocalValue(checked(name), checked(expr), ast.data))
            }
        }

    /**
     * functionDeclaration (used by declaration)
     *   : modifiers? 'fun' typeParameters?
     *     (receiverType '.')?
     *     simpleIdentifier functionValueParameters
     *     (':' type)? typeConstraints?
     *     functionBody?
     *   ;
     */
    private fun functionDeclaration(ast: Ast): FailingResult {
        ast.expectKind(functionDeclaration)

        // TODO: at some point, data functions should be supported
        return ast.unsupported(FunctionDeclaration)
    }

    /**
     * assignment : (([directlyAssignableExpression] '=') | -(assignableExpression assignmentAndOperator)) [expression]
     */
    private fun assignment(ast: Ast): ElementResult<Assignment> =
        elementOrFailure {
            ast.expectKind(assignment)

            collectingFailure(ast, assignmentAndOperator) {
                it?.let { ast.unsupportedBecause(it, AugmentingAssignment) }
            }

            val lhs = checkForFailure(directlyAssignableExpression(ast.child(directlyAssignableExpression)))
            val expr = checkForFailure(expression(ast.child(expression)))

            elementIfNoFailures {
                Element(Assignment(checked(lhs), checked(expr), ast.data))
            }
        }

    /**
     * directlyAssignableExpression (used by assignment, parenthesizedDirectlyAssignableExpression)
     *   : [postfixUnaryExpression] [assignableSuffix]
     *   | [simpleIdentifier]
     *   | parenthesizedDirectlyAssignableExpression (== '(' [directlyAssignableExpression] ')')
     *   ;
     */
    private fun directlyAssignableExpression(ast: Ast): ElementResult<PropertyAccess> =
        elementOrFailure {
            ast.expectKind(directlyAssignableExpression)

            ast.findSingleChild(simpleIdentifier)?.let {
                val name = checkForFailure(simpleIdentifier(it))
                return@elementOrFailure elementIfNoFailures {
                    Element(PropertyAccess(null, checked(name), ast.data))
                }
            }

            ast.findSingleChild(parenthesizedDirectlyAssignableExpression)?.let {
                val child = ast.child(directlyAssignableExpression)
                return@elementOrFailure directlyAssignableExpression(child)
            }

            val left = ast.child(postfixUnaryExpression)
            val postfixUnary = checkForFailure(postfixUnaryExpression(left))
            val suffixAsName = checkForFailure(assignableSuffix(ast.child(assignableSuffix)))
            elementIfNoFailures {
                Element(PropertyAccess(checked(postfixUnary), checked(suffixAsName), ast.data))
            }
        }

    /**
     * postfixUnaryExpression
     *   : [primaryExpression] [postfixUnarySuffix]*
     */
    private fun postfixUnaryExpression(ast: Ast): ElementResult<Expr> =
        elementOrFailure {
            ast.expectKind(postfixUnaryExpression)

            val primary = checkForFailure(primaryExpression(ast.child(primaryExpression)))
            val suffixes = ast.children(postfixUnarySuffix).map { checkForFailure(postfixUnarySuffix(it)) }

            elementIfNoFailures {
                suffixes.fold(primary.value) { acc, it ->
                    acc.flatMap { accExpr ->
                        when (val suffix = checked(it)) {
                            is UnarySuffix.CallSuffix -> {
                                if (accExpr !is PropertyAccess) {
                                    return@flatMap failNow(
                                        UnsupportedConstruct(
                                            ast.data,
                                            ast.data /* TODO */,
                                            InvokeOperator
                                        )
                                    )
                                }
                                val receiver = accExpr.receiver
                                val name = accExpr.name
                                Element(FunctionCall(receiver, name, suffix.arguments, suffix.ast.data))
                            }

                            is UnarySuffix.NavSuffix -> {
                                Element(PropertyAccess(accExpr, suffix.name, suffix.ast.data))
                            }
                        }
                    }
                }
            }
        }

    /**
     * postfixUnarySuffix
     *   : -postfixUnaryOperator
     *   | typeArguments
     *   | callSuffix
     *   | -indexingSuffix
     *   | navigationSuffix
     */

    private fun postfixUnarySuffix(ast: Ast): SyntacticResult<UnarySuffix> {
        ast.expectKind(postfixUnarySuffix)

        // TODO: support postfix double bang operator?
        val child = ast.singleChild()
        return when (child.kind) {
            postfixUnaryOperator -> ast.unsupportedBecause(child, UnsupportedOperator)
            typeArguments -> ast.unsupportedBecause(child, GenericExpression)
            indexingSuffix -> ast.unsupportedBecause(child, Indexing)
            callSuffix -> callSuffix(child)
            navigationSuffix -> navigationSuffix(child).flatMap { Syntactic(UnarySuffix.NavSuffix(it, ast)) }
            else -> error("unexpected child kind")
        }
    }

    /**
     * callSuffix
     *   : -typeArguments? ((valueArguments? annotatedLambda) | valueArguments)
     */
    private fun callSuffix(ast: Ast): SyntacticResult<UnarySuffix> =
        syntacticOrFailure {
            ast.expectKind(callSuffix)

            collectingFailure(ast, typeArguments) { it?.unsupportedIn(ast, GenericExpression) }
            val valueArguments = ast.findChild(valueArguments)?.let {
                checkForFailure(valueArguments(it))
            }
            val finalLambda = ast.findChild(annotatedLambda)?.let {
                checkForFailure(annotatedLambda(it))
            }
            syntacticIfNoFailures {
                val valueArgs = valueArguments?.let(::checked).orEmpty()
                val lambda = finalLambda?.let(::checked)
                Syntactic(UnarySuffix.CallSuffix(valueArgs, lambda, ast))
            }
        }

    /**
     * annotatedLambda (used by callSuffix)
     *   : -annotation* -label? lambdaLiteral
     *
     * lambdaLiteral (used by annotatedLambda, functionLiteral)
     *   : '{' (-lambdaParameters? '->')? statements '}'
     *
     */
    private fun annotatedLambda(ast: Ast): ElementResult<FunctionArgument.Lambda> =
        elementOrFailure {
            ast.expectKind(annotatedLambda)

            collectingFailure(ast, annotation) { it?.unsupportedIn(ast, AnnotationUsage) }
            collectingFailure(ast, label) { it?.unsupportedIn(ast, ControlFlow) }
            val literal = ast.child(lambdaLiteral)
            collectingFailure(literal, lambdaParameter) { it?.unsupportedIn(literal, LambdaWithParameters) }
            val statements = checkForFailure(statements(literal.child(statements)))
            elementIfNoFailures {
                Element(FunctionArgument.Lambda(Block(checked(statements), ast.data), ast.data))
            }
        }

    private fun statements(ast: Ast): SyntacticResult<List<DataStatement>> =
        syntacticOrFailure() {
            ast.expectKind(statements)
            val statements = ast.children(statement)
            val statementResult = statements.map { checkForFailure(statement(it)) }
            syntacticIfNoFailures {
                Syntactic(statementResult.map { checked(it) })
            }
        }

    /**
     * ueArguments
     *   : '(' (valueArgument (',' valueArgument)* ','?)? ')'
     */
    private fun valueArguments(ast: Ast): SyntacticResult<List<FunctionArgument.ValueArgument>> =
        syntacticOrFailure {
            ast.expectKind(valueArguments)

            val args = ast.children(valueArgument).map { checkForFailure(valueArgument(it)) }
            syntacticIfNoFailures {
                Syntactic(args.map(::checked))
            }
        }

    /**
     * valueArgument
     *    : annotation? (simpleIdentifier '=')? '*'? expression
     */
    private fun valueArgument(ast: Ast): SyntacticResult<FunctionArgument.ValueArgument> =
        syntacticOrFailure {
            ast.expectKind(valueArgument)

            collectingFailure(ast, annotation) { it?.unsupportedIn(ast, AnnotationUsage) }
            collectingFailure(ast, asterisk) { it?.unsupportedIn(ast, UnsupportedOperator) }
            val name = checkNullableForFailure(ast.findChild(simpleIdentifier)?.let { simpleIdentifier(it) })
            val expr = checkForFailure(expression(ast.child(expression)))
            syntacticIfNoFailures {
                val checkedExpr = checked(expr)
                Syntactic(
                    if (name != null)
                        FunctionArgument.Named(checked(name), checkedExpr, ast.data)
                    else FunctionArgument.Positional(checkedExpr, ast.data)
                )
            }
        }

    sealed interface UnarySuffix {
        val ast: Ast

        class CallSuffix(
            private val valueArguments: List<FunctionArgument.ValueArgument>,
            private val lambda: FunctionArgument.Lambda?,
            override val ast: Ast
        ) : UnarySuffix {
            val arguments: List<FunctionArgument> get() = valueArguments + listOfNotNull(lambda)
        }

        class NavSuffix(val name: String, override val ast: Ast) : UnarySuffix
    }

    /**
     * primaryExpression
     *   : parenthesizedExpression == '(' expression ')
     *   | simpleIdentifier
     *   | literalConstant
     *   | stringLiteral
     *   | -callableReference
     *   | -functionLiteral
     *   | -objectLiteral
     *   | collectionLiteral
     *   | -thisExpression
     *   | -superExpression
     *   | -ifExpression
     *   | -whenExpression
     *   | -tryExpression
     *   | -jumpExpression
     *   ;
     */
    private fun primaryExpression(ast: Ast): ElementResult<Expr> {
        return elementOrFailure() {
            ast.expectKind(primaryExpression)

            val singleChild = ast.childrenOrEmpty.singleOrNull() ?: error("expected a single child")

            when (singleChild.kind) {
                parenthesizedExpression -> expression(singleChild.child(expression))
                simpleIdentifier -> {
                    val simpleIdentifier = checkForFailure(simpleIdentifier(singleChild))
                    elementIfNoFailures {
                        Element(PropertyAccess(null, checked(simpleIdentifier), ast.data))
                    }
                }

                literalConstant -> literalConstant(singleChild)
                stringLiteral -> stringLiteral(singleChild)
                thisExpression -> Element(This(singleChild.data))
                callableReference -> ast.unsupported(CallableReference)
                functionLiteral -> ast.unsupported(FunctionDeclaration)
                objectLiteral -> ast.unsupported(TypeDeclaration)
                collectionLiteral -> ast.unsupported(CollectionLiteral)
                superExpression -> ast.unsupported(SupertypeUsage)
                ifExpression, whenExpression -> ast.unsupported(ConditionalExpression)
                tryExpression, jumpExpression -> ast.unsupported(ControlFlow)
                else -> error("unexpected child kind ${singleChild.kind}")
            }
        }
    }

    /**
     * literalConstant (used by primaryExpression)
     *   : BooleanLiteral
     *   | IntegerLiteral
     *   | -HexLiteral
     *   | -BinLiteral
     *   | -CharacterLiteral
     *   | -RealLiteral
     *   | 'null'
     *   | LongLiteral
     *   | UnsignedLiteral
     *   ;
     */
    private fun literalConstant(ast: Ast): ElementResult<Expr> {
        return elementOrFailure() {
            ast.expectKind(literalConstant)
            val singleChild = ast.singleChild()
            elementIfNoFailures {
                when (singleChild.kind) {
                    booleanLiteral -> Element(
                        Literal.BooleanLiteral(
                            singleChild.text.toBooleanStrict(),
                            singleChild.data
                        )
                    )

                    integerLiteral -> Element(Literal.IntLiteral(singleChild.text.toInt(), singleChild.data))
                    longLiteral -> Element(
                        Literal.LongLiteral(singleChild.text.removeSuffix("l").removeSuffix("L").toLong(), singleChild.data)
                    )

                    unsignedLiteral -> ast.unsupported(TodoNotCoveredYet)
                    binLiteral, hexLiteral, characterLiteral, realLiteral -> ast.unsupported(UnsupportedLiteral)
                    else -> error("unexpected child kind")
                }
            }
        }

    }

    private fun stringLiteral(ast: Ast): Element<Expr> {
        // TODO: support or properly reject string interpolation!
        return workaround(
            "String literals are simple to parse right away",
            run {
                val text = ast.findSingleDescendant { it.kind == lineStringText || it.kind == multiLineStringText }?.text ?: ""
                Element(Literal.StringLiteral(text, ast.data))
            })
    }

    /**
     * assignableSuffix : -typeArguments | -indexingSuffix | navigationSuffix
     */
    private fun assignableSuffix(ast: Ast): SyntacticResult<String> =
        syntacticOrFailure {
            ast.expectKind(assignableSuffix)
            val child = ast.singleChild()
            syntacticIfNoFailures {
                when (child.kind) {
                    typeArguments -> ast.unsupportedBecause(child, GenericExpression)
                    indexingSuffix -> ast.unsupportedBecause(child, Indexing)
                    navigationSuffix -> navigationSuffix(child)
                    else -> error("unexpected child kind")
                }
            }
        }

    /**
     * navigationSuffix : memberAccessOperator (simpleIdentifier | -parenthesizedExpression | -'class')
     */
    private fun navigationSuffix(ast: Ast): SyntacticResult<String> =
        syntacticOrFailure {
            // TODO: support safe access
            // TODO: support class references
            collectingFailure(ast, parenthesizedExpression) { it?.unsupportedIn(ast, InvalidLanguageConstruct) }
            collectingFailure(ast, classKeyword) { it?.unsupportedIn(ast, Reflection) }
            checkForFailure(memberAccessOperatorAsDot(ast.child(memberAccessOperator)))

            syntacticIfNoFailures { simpleIdentifier(ast.child(simpleIdentifier)) }
        }

    /**
     * memberAccessOperator : '.' | -safeNav | -'::'
     */
    private fun memberAccessOperatorAsDot(ast: Ast): SyntacticResult<Unit> {
        ast.expectKind(memberAccessOperator)
        return when (ast.singleChild().kind) {
            dot -> Syntactic(Unit)
            safeNav -> ast.unsupported(SafeNavigation)
            colonColon -> ast.unsupported(Reflection)
            else -> error("unexpected operator kind")
        }
    }

    /**
     * variableDeclaration : -annotation* simpleIdentifier (':' -type)?
     */
    private fun variableDeclaration(ast: Ast): SyntacticResult<String> =
        syntacticOrFailure {
            ast.expectKind(variableDeclaration)

            collectingFailure(ast, annotation) { it?.let { ast.unsupportedBecause(it, AnnotationUsage) } }
            // TODO: support explicit variable types
            collectingFailure(ast, type) { it?.let { ast.unsupportedBecause(it, ExplicitVariableType) } }

            syntacticIfNoFailures {
                simpleIdentifier(ast.child(simpleIdentifier))
            }
        }

    private fun simpleIdentifier(ast: Ast): Syntactic<String> {
        ast.expectKind(simpleIdentifier)
        return Syntactic(workaround("a lot of branching for soft keywords", ast.text))
    }

    /**
     * expression : disjunction
     * disjunction
     */
    private fun expression(ast: Ast): ElementResult<Expr> = elementOrFailure {
        ast.expectKind(expression)

        val disjunction = ast.child(disjunction)
        if (disjunction.children(conjunction).size > 1) {
            // TODO: support binary operators?
            return@elementOrFailure disjunction.unsupported(UnsupportedOperator)
        }
        val conjunction = disjunction.child(conjunction)
        if (conjunction.children(equality).size > 1) {
            // TODO: support binary operators?
            return@elementOrFailure conjunction.unsupported(UnsupportedOperator)
        }
        val equality = conjunction.child(equality)
        if (equality.children(comparison).size > 1) {
            // TODO: support binary operators?
            return@elementOrFailure equality.unsupported(UnsupportedOperator)
        }
        val comparison = equality.child(comparison)
        if (comparison.children(genericCallLikeComparison).size > 1) {
            // TODO: support binary operators?
            return@elementOrFailure comparison.unsupported(UnsupportedOperator)
        }

        val genericCallLikeComparison = comparison.child(genericCallLikeComparison)
        if (genericCallLikeComparison.hasChild(callSuffix)) {
            return@elementOrFailure genericCallLikeComparison.unsupported(TodoNotCoveredYet)
        }

        val infixOperation = genericCallLikeComparison.child(infixOperation)

        if (infixOperation.hasChild(inOperator) || infixOperation.hasChild(isOperator)) {
            return@elementOrFailure infixOperation.unsupported(UnsupportedOperator)
        }

        val elvisExpression = infixOperation.child(elvisExpression)
        if (elvisExpression.children(infixFunctionCall).size > 1) {
            return@elementOrFailure elvisExpression.unsupported(UnsupportedOperator)
        }

        val infixFunctionCall = elvisExpression.child(infixFunctionCall)

        elementIfNoFailures {
            if (infixFunctionCall.hasChild(simpleIdentifier)) {
                elementOrFailure {
                    val children = infixFunctionCall.children(rangeExpression)
                    val leftExpr =
                        checkForFailure(workaroundPostfixUnaryExpression(children[0]))
                    val rightExprs = children.drop(1).map {
                        checkForFailure(workaroundPostfixUnaryExpression(it))
                    }
                    val names =
                        infixFunctionCall.children(simpleIdentifier).map { checkForFailure(simpleIdentifier(it)) }
                    elementIfNoFailures {
                        Element(
                            rightExprs.zip(names).fold(checked(leftExpr)) { acc, (rExp, name) ->
                                val arg = checked(rExp)
                                FunctionCall(
                                    acc,
                                    checked(name),
                                    listOf(FunctionArgument.Positional(arg, infixFunctionCall.data)),
                                    infixOperation.data
                                )
                            }
                        )
                    }
                }
            } else {
                workaroundPostfixUnaryExpression(infixFunctionCall)
            }
        }
    }

    private fun workaroundPostfixUnaryExpression(ast: Ast): ElementResult<Expr> {
        val exprAst = workaround(
            "the expression structure is too nested for now",
            ast.flattenTo { it.kind == postfixUnaryExpression }
        ) ?: return ast.unsupported(TodoNotCoveredYet)
        return postfixUnaryExpression(exprAst)
    }

    private fun <T : Any?> workaround(@Suppress("UNUSED_PARAMETER") reason: String, value: T): T = value

    private fun <T : LanguageTreeElement> elementOrFailure(
        evaluate: FailureCollectorContext.() -> ElementResult<T>
    ): ElementResult<T> {
        val context = FailureCollectorContext()
        return evaluate(context)
    }

    private fun <T> syntacticOrFailure(
        evaluate: FailureCollectorContext.() -> SyntacticResult<T>
    ): SyntacticResult<T> {
        val context = FailureCollectorContext()
        return evaluate(context)
    }

    private fun Ast.expectKind(expected: AstKind) {
        check(kind == expected) { "invoked an AST-visiting function on an unexpected AST kind: $kind instead of $expected" }
    }

    private class FailureCollectorContext {
        private val currentFailures: MutableList<FailingResult> = mutableListOf()

        val failures: List<FailingResult> get() = currentFailures

        // TODO: introduce a type for a result with definitely collected failure?
        fun <T> checkForFailure(result: SyntacticResult<T>): CheckedResult<SyntacticResult<T>> =
            CheckedResult(collectingFailure(result))

        fun <T> checkNullableForFailure(result: SyntacticResult<T>?): CheckedResult<SyntacticResult<T>>? =
            if (result == null) null else CheckedResult(collectingFailure(result))

        fun <T : LanguageTreeElement> checkForFailure(result: ElementResult<T>): CheckedResult<ElementResult<T>> =
            CheckedResult(collectingFailure(result))

        fun failNow(failingResult: FailingResult): FailingResult {
            collectingFailure(failingResult)
            return syntacticIfNoFailures<Nothing> { error("expected a failure") } as FailingResult
        }

        fun collectingFailure(ast: Ast, astKind: AstKind, failureTest: (Ast?) -> FailingResult?) {
            val child = ast.findChild(astKind)
            val maybeFailure = child.run(failureTest)
            collectingFailure(maybeFailure)
        }

        fun collectingFailure(maybeFailure: FailingResult?) {
            if (maybeFailure != null) {
                currentFailures.add(maybeFailure)
            }
        }

        fun <T> collectingFailure(result: T): T {
            when (result) {
                is FailingResult -> currentFailures.add(result)
            }
            return result
        }

        interface CheckBarrierContext {
            fun <T : LanguageTreeElement> checked(result: CheckedResult<ElementResult<T>>): T {
                val value = result.value
                check(value is Element)
                return value.element
            }

            fun <T> checked(result: CheckedResult<SyntacticResult<T>>): T {
                val value = result.value
                check(value is Syntactic)
                return value.value
            }

            fun <T> checked(results: List<CheckedResult<SyntacticResult<T>>>): List<T> =
                results.map {
                    val syntacticResult = it.value
                    check(syntacticResult is Syntactic)
                    syntacticResult.value
                }.toList()
        }

        fun <T : LanguageTreeElement> elementIfNoFailures(evaluate: CheckBarrierContext.() -> ElementResult<T>): ElementResult<T> =
            when (currentFailures.size) {
                0 -> evaluate(object : CheckBarrierContext {})
                1 -> currentFailures.single()
                else -> MultipleFailuresResult(currentFailures)
            }

        fun <T> syntacticIfNoFailures(evaluate: CheckBarrierContext.() -> SyntacticResult<T>): SyntacticResult<T> =
            when (currentFailures.size) {
                0 -> evaluate(object : CheckBarrierContext {})
                1 -> currentFailures.single()
                else -> MultipleFailuresResult(currentFailures)
            }

        class CheckedResult<T : LanguageResult<*>>(val value: T)
    }

    private val Ast.data get() = sourceData(sourceIdentifier)

    private fun Ast.unsupported(
        feature: UnsupportedLanguageFeature
    ) = UnsupportedConstruct(this.data, this.data, feature)

    private fun Ast.unsupportedIn(
        outer: Ast,
        feature: UnsupportedLanguageFeature
    ) = UnsupportedConstruct(outer.data, this.data, feature)

    private fun Ast.unsupportedBecause(
        erroneousAst: Ast,
        feature: UnsupportedLanguageFeature
    ) = UnsupportedConstruct(this.data, erroneousAst.data, feature)
}
