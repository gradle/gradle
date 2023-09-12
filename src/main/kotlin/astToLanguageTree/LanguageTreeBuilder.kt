package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.*
import com.h0tk3y.kotlin.staticObjectNotation.AstKind.*
import com.h0tk3y.kotlin.staticObjectNotation.ElementOrFailureResult.*
import com.h0tk3y.kotlin.staticObjectNotation.LanguageModelUnsupportedConstruct.*
import kotlinx.ast.common.ast.Ast

interface LanguageTreeBuilder {
    fun build(ast: Ast): LanguageTreeResult
}

class LanguageTreeBuilderWithTopLevelBlock(
    private val delegate: LanguageTreeBuilder
) : LanguageTreeBuilder {
    override fun build(ast: Ast): LanguageTreeResult {
        val result = delegate.build(ast)
        val (topLevelStatements, others) = result.results.partition { it is ElementResult && it.element is DataStatement }
        val topLevelBlock = Block(topLevelStatements.map { (it as ElementResult).element as DataStatement }, ast)
        return LanguageTreeResult(others + element(topLevelBlock))
    }
}

class DefaultLanguageTreeBuilder : LanguageTreeBuilder {
    override fun build(ast: Ast): LanguageTreeResult =
        LanguageTreeResult(buildList {
            ast.childrenOrEmpty.forEach { child ->
                when (child.kind) {
                    packageHeader -> addAll(checkPackageHeader(child))
                    importList -> addAll(parseImports(child))
                    statement -> add(parseStatement(child))
                    semi, endOfFile, lineCommentTerminal, delimitedCommentTerminal, newline, whitespace -> Unit

                    else -> throw IllegalArgumentException("unexpected top-level AST entry:\n${child.text}")
                }
            }
        })
}

fun checkPackageHeader(ast: Ast): List<ElementOrFailureResult<Nothing>> =
    if (ast.childrenOrEmpty.isEmpty())
        emptyList()
    else
        listOf(unsupported(PackageHeader(ast)))

fun parseImports(importListAst: Ast): Sequence<ElementOrFailureResult<Import>> {
    check(importListAst.kind == importList)
    return importListAst.traverse().filter { it.kind == importHeader }.map(::parseImport)
}

fun parseImport(ast: Ast): ElementOrFailureResult<Import> {
    check(ast.kind == importHeader)
    if (ast.hasDescendant { it.kind == asterisk }) {
        return unsupported(StarImport(ast))
    }
    if (ast.hasDescendant { it.kind == asKeyword }) {
        return unsupported(RenamingImport(ast))
    }

    val identifier = ast.findSingleDescendant { it.kind == identifier }
        ?: throw IllegalArgumentException("The import AST must have an identifier")

    val idParts = AccessChain(
        identifier.traverse().filter { it.kind == identifierTerminal }.map { it.text }.toList(),
        identifier
    )
    return element(Import(idParts, ast))
}

fun parseStatements(ast: Ast): ElementOrFailureResult<Block> {
    check(ast.kind == statements)

    val statements = ast.childrenOrEmpty.filter { it.kind == statement }.map { parseStatement(it) }
    return if (statements.all { it is ElementResult }) {
        element(Block(statements.map { (it as ElementResult).element }, ast))
    } else {
        MultipleFailuresResult(statements.filterIsInstance<UnsupportedConstruct>())
    }
}

fun parseStatement(ast: Ast): ElementOrFailureResult<DataStatement> {
    check(ast.kind == statement)

    val flattened = ast.flatten()
    return when (flattened.kind) {
        propertyDeclaration -> parseLocalValPropertyDeclaration(flattened)
        assignment -> parseAssignment(flattened)
        postfixUnaryExpression -> parsePostfixUnary(ast)
        classDeclaration, typeAlias -> unsupported(TypeDeclaration(ast))
        else -> unsupported(TodoNotCoveredYet(ast))
    }
}

private fun parseAssignment(ast: Ast): ElementOrFailureResult<Assignment> {
    check(ast.kind == assignment)

    val lhs = ast.child(directlyAssignableExpression)

    val assignableSuffix = lhs.findDescendant { it.kind == assignableSuffix }
    val indexingSuffix = assignableSuffix?.findDescendant { it.kind == indexingSuffix }
    if (indexingSuffix != null) {
        return unsupported(IndexedAssignment(ast, indexingSuffix))
    }

    val lhsChain = parseAccessChain(lhs)
    return when (val rhsExpr = parseExpression(ast.child(expression))) {
        is FailingResult -> when (lhsChain) {
            is FailingResult -> MultipleFailuresResult(listOf(rhsExpr, lhsChain))
            is ElementResult -> rhsExpr
        }

        is ElementResult -> when (lhsChain) {
            is FailingResult -> lhsChain
            is ElementResult -> element(Assignment(lhsChain.element, rhsExpr.element, ast))
        }
    }
}

private fun parseAccessChain(
    originAst: Ast
): ElementOrFailureResult<AccessChain> {
    if (originAst.kind == identifierTerminal) {
        return element(AccessChain(listOf(originAst.text), originAst))
    }

    if (
        originAst.childrenOrEmpty.size == 1 &&
        (originAst.hasChild(simpleIdentifier) || originAst.hasChild(identifierTerminal))
    ) {
        return element(AccessChain(listOf(originAst.text), originAst))
    }

    val postfixUnaryExpression =
        if (originAst.kind != postfixUnaryExpression) originAst.child(AstKind.postfixUnaryExpression) else originAst

    val primaryId = postfixUnaryExpression.child(primaryExpression).flatten().takeIf { it.kind == identifierTerminal }
        ?: TODO("Unexpected assignment lhs")

    val assignableSuffix = originAst.findDescendant { it.kind == AstKind.assignableSuffix }
    val postfixUnarySuffixes = postfixUnaryExpression.children { it.kind == postfixUnarySuffix }

    val erroneousIndexingInSuffixes = postfixUnarySuffixes.filter { it.hasChild(indexingSuffix) }
    if (erroneousIndexingInSuffixes.isNotEmpty()) {
        return unsupported(
            IndexedAssignment(
                originAst,
                erroneousIndexingInSuffixes.first { it.hasChild(indexingSuffix) })
        )
    }

    val erroneousCallInSuffixes = postfixUnarySuffixes.withIndex().find {
        (assignableSuffix != null || it.index != postfixUnarySuffixes.lastIndex) && it.value.hasChild(callSuffix)
    }?.value
    if (erroneousCallInSuffixes != null) {
        return unsupported(FunctionCallInAccessChain(originAst, erroneousCallInSuffixes))
    }

    val navSuffixes =
        postfixUnarySuffixes.filter { it.hasChild { it.kind == navigationSuffix } }

    val navSuffixIds =
        navSuffixes
            .map { suffix ->
                suffix.findSingleDescendant(identifierTerminal) ?: TODO("Unexpected assignment lhs")
            }
    val assignableSuffixId =
        assignableSuffix?.child(navigationSuffix)?.child(simpleIdentifier)?.child(identifierTerminal)
    return element(
        AccessChain(
            listOfNotNull(primaryId, *navSuffixIds.toTypedArray<Ast>(), assignableSuffixId).map { it.text },
            originAst
        )
    )
}

private fun parseLocalValPropertyDeclaration(ast: Ast): ElementOrFailureResult<LocalValue> {
    if (ast.hasChild(varKeyword)) {
        return unsupported(VarUnsupported(ast, ast.child(varKeyword)))
    }

    if (ast.hasChild(modifiers)) {
        return unsupported(ValModifierUnsupported(ast, ast.child(modifiers)))
    }

    val name = ast.findDescendant { it.kind == identifierTerminal }?.text
        ?: throw IllegalArgumentException("The property declaration AST must have a declared name")

    val typeLabel = ast.child(variableDeclaration).findSingleChild(type)
    if (typeLabel != null) {
        return unsupported(ExplicitVariableType(ast, typeLabel))
    }

    val rhs = ast.findSingleChild(expression)
        ?: throw IllegalArgumentException("The property declaration AST must have an expression RHS")

    return when (val expr = parseExpression(rhs)) {
        is FailingResult -> expr
        is ElementResult -> element(LocalValue(name, expr.element, ast))
    }
}

private fun parseExpression(ast: Ast): ElementOrFailureResult<Expr> {
    val expr = ast.flatten()
    return when (expr.kind) {
        integerLiteral -> element(Literal.IntLiteral(expr.text.toInt(), expr))
        booleanLiteral -> element(Literal.BooleanLiteral(expr.text.toBooleanStrict(), expr))
        lineStringLiteral, multiLineStringLiteral -> {
            val textTerminal =
                expr.findSingleDescendant { it.kind == lineStringText || it.kind == multiLineStringText }
                    ?: throw IllegalArgumentException("The string literal AST must have a *Text terminal")
            element(Literal.StringLiteral(textTerminal.text, expr))
        }

        postfixUnaryExpression -> parsePostfixUnary(expr)
        identifierTerminal -> parseAccessChain(expr)
        else -> unsupported(TodoNotCoveredYet(ast))
    }
}

private fun parsePostfixUnary(ast: Ast): ElementOrFailureResult<Expr> {
    val flattened = ast.flatten()

    val access = parseAccessChain(flattened)
    when (access) {
        is FailingResult -> return access
        is ElementResult -> Unit
    }

    val callUnarySuffix =
        flattened.findSingleChild { child -> child.kind == postfixUnarySuffix && child.hasChild(callSuffix) }
    val callSuffix = callUnarySuffix?.child(callSuffix)
    if (callSuffix == null)
        return access
    else {
        val valueArgResults = if (callSuffix.hasChild(valueArguments))
            callSuffix.child(valueArguments).children(valueArgument).map(::parseFunctionValueArgument)
        else emptyList()
        val failures = valueArgResults.filterIsInstance<FailingResult>()
        // TODO: report these failures together with the failures in parsing the lambda
        val valueArgs = when (failures.size) {
            0 -> valueArgResults.map { (it as ElementResult).element }
            1 -> return failures.single()
            else -> return MultipleFailuresResult(failures)
        }
        
        if (callSuffix.findSingleChild(annotatedLambda) != null) {
            val bodyAst = callSuffix.findDescendant { it.kind == statements }
                ?: throw IllegalArgumentException("Expected statements inside the lambda")
            return when (val body = parseStatements(bodyAst)) {
                is ElementResult -> {
                    val lambda = FunctionArgument.Lambda(body.element, bodyAst)
                    element(FunctionCall(access.element, valueArgs + listOf(lambda), ast))
                }

                is FailingResult -> body
            }
        } else {
            return element(FunctionCall(access.element, valueArgs, ast))
        }
    }
}

private fun parseFunctionValueArgument(ast: Ast): ElementOrFailureResult<FunctionArgument> {
    check(ast.kind == valueArgument)
    return when (val expr = parseExpression(ast.child(expression))) {
        is FailingResult -> expr
        is ElementResult -> {
            val exprElement = expr.element
            if (ast.hasChild(simpleIdentifier)) {
                element(FunctionArgument.Named(ast.child(simpleIdentifier).text, exprElement, ast))
            } else {
                element(FunctionArgument.Positional(exprElement, ast))
            }
        }
    }
}

internal fun <T : LanguageTreeElement> element(element: T) =
    ElementResult(element)

internal fun unsupported(construct: LanguageModelUnsupportedConstruct) =
    UnsupportedConstruct(construct)

fun main() {
    val code = """
        class A { }
    """.trimIndent()

    val asts = parseToAst(code)

    val builder = DefaultLanguageTreeBuilder()
    val result = asts.map(builder::build)
    println(result)
}