package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.AstKind.*
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedLanguageFeature.*
import com.h0tk3y.kotlin.staticObjectNotation.language.*
import kotlinx.ast.common.ast.Ast

object GrammarToTree {
    /**
     * script : -shebangLine? -fileAnnotation* -packageHeader importList (statement semi)* EOF
     */
    fun script(ast: Ast): SyntacticResult<List<ElementResult<*>>> {
        ast.expectKind(script)
        val result = syntacticOrFailure {
            collectingFailure(ast.findChild(shebangLine)?.unsupported(UnsupportedShebangInScript))
            collectingFailure(
                ast.findChild(packageHeader)?.takeIf { it.childrenOrEmpty.isNotEmpty() }?.unsupported(PackageHeader)
            )
            val imports = importList(ast.child(importList))
            val statements = ast.children(statement).map { statement(it) }

            syntacticAfterBarrier {
                Syntactic(failures + imports + statements)
            }
        }
        return result
    }

    fun importList(ast: Ast): List<ElementResult<Import>> {
        ast.expectKind(importList)
        return ast.children(importHeader).map { importHeader(it) }
    }

    fun importHeader(ast: Ast): ElementResult<Import> = elementOrFailure {
        ast.expectKind(importHeader)

        collectingFailure(ast.findChild(asterisk)?.unsupported(StarImport))
        collectingFailure(ast.findChild(importAlias)?.unsupported(RenamingImport))

        elementAfterBarrier {
            val ident = ast.child(identifier)
            val names = ident.children(simpleIdentifier).map { simpleIdentifier(it).value }
            Element(Import(AccessChain(names, ident), ast))
        }
    }

    fun statements(ast: Ast): SyntacticResult<List<DataStatement>> = syntacticOrFailure {
        ast.expectKind(statements)
        val statements = ast.children(statement)
        val statementResult = statements.map { collectingFailure(statement(it)) }
        syntacticAfterBarrier {
            Syntactic(statementResult.map { checked(it) })
        }
    }

    /**
     * statement : (-label | -annotation)* ([declaration] | [assignment] | -loopStatement | [expression])
     */
    fun statement(ast: Ast): ElementResult<DataStatement> = elementOrFailure {
        ast.expectKind(statement)

        collectingFailure(ast.findChild(label)?.let { ast.unsupportedBecause(it, LabelledStatement) })
        collectingFailure(ast.findChild(annotation)?.let { ast.unsupportedBecause(it, AnnotationUsage) })

        val singleChild =
            ast.childrenOrEmpty.singleOrNull { it.kind != label && it.kind != annotation && it.kind != whitespace }
                ?: error("expected a single child")
        
        elementAfterBarrier {
            when (singleChild.kind) {
                loopStatement -> failNow(singleChild.unsupported(LoopStatement))
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
    fun declaration(ast: Ast): ElementResult<DataStatement> {
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
    fun propertyDeclaration(ast: Ast): ElementResult<LocalValue> = elementOrFailure {
        ast.expectKind(propertyDeclaration)

        collectingFailure(ast.findChild(modifiers)?.let { ast.unsupportedBecause(it, ValModifierNotSupported) })
        collectingFailure(ast.findChild(varKeyword)?.let { ast.unsupportedBecause(it, LocalVarNotSupported) })
        collectingFailure(ast.findChild(receiverType)?.let { ast.unsupportedBecause(it, ExtensionProperty) })
        collectingFailure(ast.findChild(multiVariableDeclaration)?.let { ast.unsupportedBecause(it, MultiVariable) })
        collectingFailure(ast.findChild(typeConstraints)?.let { ast.unsupportedBecause(it, GenericDeclaration) })
        collectingFailure(ast.findChild(propertyDelegate)?.let { ast.unsupportedBecause(it, DelegatedProperty) })
        collectingFailure(ast.findChild(getter)?.let { ast.unsupportedBecause(it, CustomAccessor) })
        collectingFailure(ast.findChild(setter)?.let { ast.unsupportedBecause(it, CustomAccessor) })

        val name = collectingFailure(variableDeclaration(ast.child(variableDeclaration)))
        val expr = collectingFailure(
            ast.findChild(expression)?.let(::expression) ?: ast.unsupported(UninitializedProperty)
        )

        elementAfterBarrier {
            Element(LocalValue(checked(name), checked(expr), ast))
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
    fun functionDeclaration(ast: Ast): FailingResult {
        ast.expectKind(functionDeclaration)

        // TODO: at some point, data functions should be supported
        return ast.unsupported(FunctionDeclaration)
    }

    /**
     * assignment : (([directlyAssignableExpression] '=') | -(assignableExpression assignmentAndOperator)) [expression]
     */
    fun assignment(ast: Ast): ElementResult<Assignment> = elementOrFailure {
        check(ast.kind == assignment)
        collectingFailure(
            ast.findChild(assignmentAndOperator)?.let { ast.unsupportedBecause(it, AugmentingAssignment) })

        val lhs = collectingFailure(directlyAssignableExpression(ast.child(directlyAssignableExpression)))
        val expr = collectingFailure(expression(ast.child(expression)))

        elementAfterBarrier {
            Element(Assignment(checked(lhs), checked(expr), ast))
        }
    }

    /**
     * directlyAssignableExpression (used by assignment, parenthesizedDirectlyAssignableExpression)
     *   : [postfixUnaryExpression] [assignableSuffix]
     *   | [simpleIdentifier]
     *   | parenthesizedDirectlyAssignableExpression (== '(' [directlyAssignableExpression] ')')
     *   ;
     */
    fun directlyAssignableExpression(ast: Ast): ElementResult<PropertyAccess> {
        ast.expectKind(directlyAssignableExpression)

        ast.findSingleChild(simpleIdentifier)?.let {
            val name = simpleIdentifier(it)
            return Element(PropertyAccess(null, name.value, ast))
        }

        ast.findSingleChild(parenthesizedDirectlyAssignableExpression)?.let {
            val child = ast.child(directlyAssignableExpression)
            return directlyAssignableExpression(child)
        }

        return elementOrFailure {
            val left = ast.child(postfixUnaryExpression)
            val postfixUnary = collectingFailure(postfixUnaryExpression(left))
            val suffixAsName = collectingFailure(assignableSuffix(ast.child(assignableSuffix)))
            elementAfterBarrier {
                Element(PropertyAccess(checked(postfixUnary), checked(suffixAsName), ast))
            }
        }
    }

    /**
     * postfixUnaryExpression
     *   : [primaryExpression] [postfixUnarySuffix]*
     */
    fun postfixUnaryExpression(ast: Ast): ElementResult<Expr> = elementOrFailure {
        ast.expectKind(postfixUnaryExpression)

        val primary = collectingFailure(primaryExpression(ast.child(primaryExpression)))
        val suffixes = ast.children(postfixUnarySuffix).map { collectingFailure(postfixUnarySuffix(it)) }

        elementAfterBarrier {
            suffixes.fold(primary.value) { acc, it ->
                acc.flatMap { accExpr ->
                    when (val suffix = checked(it)) {
                        is UnarySuffix.CallSuffix -> {
                            if (accExpr !is PropertyAccess) {
                                return@flatMap failNow(
                                    UnsupportedConstruct(
                                        ast,
                                        ast /* TODO */,
                                        InvokeOperator
                                    )
                                )
                            }
                            val receiver = accExpr.receiver
                            val name = accExpr.name
                            Element(FunctionCall(receiver, name, suffix.arguments, suffix.ast))
                        }

                        is UnarySuffix.NavSuffix -> {
                            Element(PropertyAccess(accExpr, suffix.name, suffix.ast))
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

    fun postfixUnarySuffix(ast: Ast): SyntacticResult<UnarySuffix> {
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
    fun callSuffix(ast: Ast): SyntacticResult<UnarySuffix> = syntacticOrFailure {
        ast.expectKind(callSuffix)

        collectingFailure(ast.findChild(typeArguments)?.unsupportedIn(ast, GenericExpression))
        val valueArguments = ast.findChild(valueArguments)?.let {
            collectingFailure(valueArguments(it))
        }
        val finalLambda = ast.findChild(annotatedLambda)?.let {
            collectingFailure(annotatedLambda(it))
        }
        syntacticAfterBarrier {
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
    fun annotatedLambda(ast: Ast): ElementResult<FunctionArgument.Lambda> = elementOrFailure {
        ast.expectKind(annotatedLambda)

        collectingFailure(ast.findChild(annotation)?.unsupportedIn(ast, AnnotationUsage))
        collectingFailure(ast.findChild(label)?.unsupportedIn(ast, ControlFlow))
        val literal = ast.child(lambdaLiteral)
        collectingFailure(literal.findChild(lambdaParameter)?.unsupportedIn(literal, LambdaWithParameters))
        val statements = collectingFailure(statements(literal.child(statements)))
        elementAfterBarrier {
            Element(FunctionArgument.Lambda(Block(checked(statements), ast), ast))
        }
    }

    /**
     * ueArguments
     *   : '(' (valueArgument (',' valueArgument)* ','?)? ')'
     */
    fun valueArguments(ast: Ast): SyntacticResult<List<FunctionArgument.ValueArgument>> = syntacticOrFailure {
        ast.expectKind(valueArguments)

        val args = ast.children(valueArgument).map { collectingFailure(valueArgument(it)) }
        syntacticAfterBarrier {
            Syntactic(args.map(::checked))
        }
    }

    /**
     * valueArgument
     *    : annotation? (simpleIdentifier '=')? '*'? expression
     */
    fun valueArgument(ast: Ast): SyntacticResult<FunctionArgument.ValueArgument> = syntacticOrFailure {
        ast.expectKind(valueArgument)

        collectingFailure(ast.findChild(annotation)?.unsupportedIn(ast, AnnotationUsage))
        collectingFailure(ast.findChild(asterisk)?.unsupportedIn(ast, UnsupportedOperator))
        val name = ast.findChild(simpleIdentifier)?.let { simpleIdentifier(it).value }
        val expr = collectingFailure(expression(ast.child(expression)))
        syntacticAfterBarrier {
            val checkedExpr = checked(expr)
            Syntactic(
                if (name != null)
                    FunctionArgument.Named(name, checkedExpr, ast)
                else FunctionArgument.Positional(checkedExpr, ast)
            )
        }
    }

    sealed interface UnarySuffix {
        val ast: Ast

        class CallSuffix(
            val valueArguments: List<FunctionArgument.ValueArgument>,
            val lambda: FunctionArgument.Lambda?,
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
    fun primaryExpression(ast: Ast): ElementResult<Expr> {
        ast.expectKind(primaryExpression)

        val singleChild = ast.childrenOrEmpty.singleOrNull() ?: error("expected a single child")

        return when (singleChild.kind) {
            parenthesizedExpression -> expression(singleChild.child(expression))
            simpleIdentifier -> Element(PropertyAccess(null, simpleIdentifier(singleChild).value, ast))
            literalConstant -> literalConstant(singleChild)
            stringLiteral -> stringLiteral(singleChild)
            thisExpression -> Element(This(singleChild))
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
    fun literalConstant(ast: Ast): ElementResult<Expr> {
        ast.expectKind(literalConstant)
        val singleChild = ast.singleChild()
        return when (singleChild.kind) {
            booleanLiteral -> Element(Literal.BooleanLiteral(singleChild.text.toBooleanStrict(), singleChild))
            integerLiteral -> Element(Literal.IntLiteral(singleChild.text.toInt(), singleChild))
            longLiteral -> Element(
                Literal.LongLiteral(singleChild.text.removeSuffix("l").removeSuffix("L").toLong(), singleChild)
            )

            unsignedLiteral -> ast.unsupported(TodoNotCoveredYet)
            binLiteral, hexLiteral, characterLiteral, realLiteral -> ast.unsupported(UnsupportedLiteral)
            else -> error("unexpected child kind")
        }
    }

    fun stringLiteral(ast: Ast): ElementResult<Expr> {
        // TODO: support or properly reject string interpolation!
        return workaround("String literals are simple to parse right away", run {
            val textTerminal =
                ast.findSingleDescendant { it.kind == lineStringText || it.kind == multiLineStringText }
                    ?: error("The string literal AST must have a *Text terminal")
            Element(Literal.StringLiteral(textTerminal.text, ast))
        })
    }

    /**
     * assignableSuffix : -typeArguments | -indexingSuffix | navigationSuffix
     */
    fun assignableSuffix(ast: Ast): SyntacticResult<String> {
        ast.expectKind(assignableSuffix)
        val child = ast.singleChild()
        return when (child.kind) {
            typeArguments -> ast.unsupportedBecause(child, GenericExpression)
            indexingSuffix -> ast.unsupportedBecause(child, Indexing)
            navigationSuffix -> navigationSuffix(child)
            else -> error("unexpected child kind")
        }
    }

    /**
     * navigationSuffix : memberAccessOperator (simpleIdentifier | -parenthesizedExpression | -'class')
     */
    fun navigationSuffix(ast: Ast): SyntacticResult<String> = syntacticOrFailure {
        // TODO: support safe access
        // TODO: support class references
        collectingFailure(ast.findChild(parenthesizedExpression)?.unsupportedIn(ast, InvalidLanguageConstruct))
        collectingFailure(ast.findChild(classKeyword)?.unsupportedIn(ast, Reflection))
        collectingFailure(memberAccessOperatorAsDot(ast.child(memberAccessOperator)))

        syntacticAfterBarrier { simpleIdentifier(ast.child(simpleIdentifier)) }
    }

    /**
     * memberAccessOperator : '.' | -safeNav | -'::'
     */
    fun memberAccessOperatorAsDot(ast: Ast): SyntacticResult<Unit> {
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
    fun variableDeclaration(ast: Ast): SyntacticResult<String> = syntacticOrFailure {
        ast.expectKind(variableDeclaration)

        collectingFailure(ast.findChild(annotation)?.let { ast.unsupportedBecause(it, AnnotationUsage) })
        // TODO: support explicit variable types
        collectingFailure(ast.findChild(type)?.let { ast.unsupportedBecause(it, ExplicitVariableType) })

        syntacticAfterBarrier {
            simpleIdentifier(ast.child(simpleIdentifier))
        }
    }

    fun simpleIdentifier(ast: Ast): Syntactic<String> {
        ast.expectKind(simpleIdentifier)
        return Syntactic(workaround("a lot of branching for soft keywords", ast.text))
    }

    /**
     * expression : disjunction
     * disjunction
     */
    fun expression(ast: Ast): ElementResult<Expr> {
        ast.expectKind(expression)

        val disjunction = ast.child(disjunction)
        if (disjunction.children(conjunction).size > 1) {
            // TODO: support binary operators?
            return disjunction.unsupported(UnsupportedOperator)
        }
        val conjunction = disjunction.child(conjunction)
        if (conjunction.children(equality).size > 1) {
            // TODO: support binary operators?
            return conjunction.unsupported(UnsupportedOperator)
        }
        val equality = conjunction.child(equality)
        if (equality.children(comparison).size > 1) {
            // TODO: support binary operators?
            return equality.unsupported(UnsupportedOperator)
        }
        val comparison = equality.child(comparison)
        if (comparison.children(genericCallLikeComparison).size > 1) {
            // TODO: support binary operators?
            return comparison.unsupported(UnsupportedOperator)
        }

        val genericCallLikeComparison = comparison.child(genericCallLikeComparison)
        if (genericCallLikeComparison.hasChild(callSuffix)) {
            return genericCallLikeComparison.unsupported(TodoNotCoveredYet)
        }

        val infixOperation = genericCallLikeComparison.child(infixOperation)

        if (infixOperation.hasChild(inOperator) || infixOperation.hasChild(isOperator)) {
            return infixOperation.unsupported(UnsupportedOperator)
        }

        val elvisExpression = infixOperation.child(elvisExpression)
        if (elvisExpression.children(infixFunctionCall).size > 1) {
            return elvisExpression.unsupported(UnsupportedOperator)
        }

        val infixFunctionCall = elvisExpression.child(infixFunctionCall)

        return if (infixFunctionCall.hasChild(simpleIdentifier)) {
            elementOrFailure {
                val children = infixFunctionCall.children(rangeExpression)
                val leftExpr = collectingFailure(workaroundPostfixUnaryExpression(children[0]))
                val rightExprs = children.drop(1).map {
                    collectingFailure(workaroundPostfixUnaryExpression(it))
                }
                val names = infixFunctionCall.children(simpleIdentifier).map { simpleIdentifier(it).value }
                elementAfterBarrier {
                    Element(
                        rightExprs.zip(names).fold(checked(leftExpr)) { acc, (rExp, name) ->
                            val arg = checked(rExp)
                            FunctionCall(
                                acc, 
                                name, 
                                listOf(FunctionArgument.Positional(arg, infixFunctionCall)),
                                infixOperation
                            )
                        }
                    )
                }
            }
        } else {
            workaroundPostfixUnaryExpression(infixFunctionCall)
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

    private fun <T : LanguageTreeElement> elementOrFailure(evaluate: FailureCollectorContext.() -> ElementResult<T>): ElementResult<T> {
        val context = FailureCollectorContext()
        return evaluate(context)
    }

    private fun <T> syntacticOrFailure(evaluate: FailureCollectorContext.() -> SyntacticResult<T>): SyntacticResult<T> {
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
        fun <T> collectingFailure(result: SyntacticResult<T>): CheckedResult<SyntacticResult<T>> =
            CheckedResult(checkForFailure(result))

        fun <T : LanguageTreeElement> collectingFailure(result: ElementResult<T>): CheckedResult<ElementResult<T>> =
            CheckedResult(checkForFailure(result))

        fun failNow(): FailingResult {
            check(currentFailures.isNotEmpty())
            return syntacticAfterBarrier<Nothing> { error("expected a failure") } as FailingResult
        }

        fun failNow(failingResult: FailingResult): FailingResult {
            collectingFailure(failingResult)
            return syntacticAfterBarrier<Nothing> { error("expected a failure") } as FailingResult
        }

        fun collectingFailure(maybeFailure: FailingResult?) {
            if (maybeFailure != null) {
                currentFailures.add(maybeFailure)
            }
        }

        fun <T> checkForFailure(result: T): T {
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
        }

        fun <T : LanguageTreeElement> elementAfterBarrier(evaluate: CheckBarrierContext.() -> ElementResult<T>): ElementResult<T> =
            when (currentFailures.size) {
                0 -> evaluate(object : CheckBarrierContext {})
                1 -> currentFailures.single()
                else -> MultipleFailuresResult(currentFailures)
            }

        fun <T> syntacticAfterBarrier(evaluate: CheckBarrierContext.() -> SyntacticResult<T>): SyntacticResult<T> =
            when (currentFailures.size) {
                0 -> evaluate(object : CheckBarrierContext {})
                1 -> currentFailures.single()
                else -> MultipleFailuresResult(currentFailures)
            }

        class CheckedResult<T : LanguageResult<*>>(val value: T)
    }
}

internal fun Ast.unsupported(
    feature: UnsupportedLanguageFeature
) = UnsupportedConstruct(this, this, feature)

internal fun Ast.unsupportedIn(
    outer: Ast,
    feature: UnsupportedLanguageFeature
) = UnsupportedConstruct(outer, this, feature)

internal fun Ast.unsupportedBecause(
    erroneousAst: Ast,
    feature: UnsupportedLanguageFeature
) = UnsupportedConstruct(this, erroneousAst, feature)