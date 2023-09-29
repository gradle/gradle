package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.AstNode
import kotlinx.ast.common.ast.AstTerminal

fun Ast.flatten(): Ast = traverse().firstOrNull { it is AstTerminal || it is AstNode && it.children.size > 1 } ?: this

fun Ast.findChild(predicate: (Ast) -> Boolean): Ast? = childrenOrEmpty.find { it != this && predicate(it) }
fun Ast.findChild(kind: AstKind): Ast? = findChild { it.kind == kind }

fun Ast.child(kind: AstKind): Ast = findChild { it.kind == kind } ?: error("AST does not have a child with kind $kind")

fun Ast.hasChild(predicate: (Ast) -> Boolean): Boolean = findChild(predicate) != null
fun Ast.hasChild(kind: AstKind) = hasChild { it.kind == kind }

fun Ast.singleChild() = childrenOrEmpty.singleOrNull() ?: error("expected a single child")
fun Ast.findSingleChild(predicate: (Ast) -> Boolean): Ast? = childrenOrEmpty.singleOrNull { it != this && predicate(it) }
fun Ast.findSingleChild(kind: AstKind) = this@findSingleChild.findSingleChild { it.kind == kind }

fun Ast.children(predicate: (Ast) -> Boolean) = childrenOrEmpty.filter(predicate)
fun Ast.children(kind: AstKind): List<Ast> = childrenOrEmpty.filter { it.kind == kind }

fun Ast.flattenTo(predicate: (Ast) -> Boolean): Ast? = 
    traverse().find {
        when {
            it != this && predicate(it) -> true
            it.childrenOrEmpty.size > 1 -> return@flattenTo null
            else -> false
        }
    }

fun Ast.findDescendant(predicate: (Ast) -> Boolean): Ast? = traverse().find { it != this && predicate(it) }
fun Ast.hasDescendant(predicate: (Ast) -> Boolean): Boolean = findDescendant(predicate) != null

fun Ast.findSingleDescendant(predicate: (Ast) -> Boolean): Ast? = traverse().singleOrNull { it != this && predicate(it) }
fun Ast.findSingleDescendant(kind: AstKind) = this@findSingleDescendant.findSingleDescendant { it.kind == kind }

fun Ast.traverse(): Sequence<Ast> =
    sequence {
        suspend fun SequenceScope<Ast>.visit(ast: Ast) {
            yield(ast)
            if (ast is AstNode) {
                ast.children.forEach { visit(it) }
            }
        }
        visit(this@traverse)
    }

val Ast.kind: AstKind get() = AstKind.entries.singleOrNull { it.astName == description } ?: AstKind.other

val Ast.childrenOrEmpty: List<Ast>
    get() = when (this) {
        is AstNode -> children
        else -> emptyList()
    }

val Ast.text: String
    get() = when (this) {
        is AstTerminal -> this.text
        else -> traverse().filterIsInstance<AstTerminal>().joinToString("") { it.text }
    }

@Suppress("EnumEntryName")
enum class AstKind(astDescription: String? = null) {
    script,
    shebangLine,
    fileAnnotation,
    importHeader,
    importList,
    importAlias,
    packageHeader,
    statements,
    statement,
    assignment,
    loopStatement,
    assignmentAndOperator,
    directlyAssignableExpression,
    parenthesizedDirectlyAssignableExpression,
    parenthesizedExpression,
    expression,
    disjunction,
    conjunction,
    equality,
    comparison,
    genericCallLikeComparison,
    infixOperation,
    infixFunctionCall,
    rangeExpression,
    elvisExpression,
    inOperator,
    isOperator,
    declaration,
    propertyDeclaration,
    propertyDelegate,
    variableDeclaration,
    multiVariableDeclaration,
    typeConstraints,
    getter,
    setter,
    receiverType,
    postfixUnaryExpression,
    classDeclaration,
    functionDeclaration,
    functionLiteral,
    objectLiteral,
    collectionLiteral,
    thisExpression,
    ifExpression,
    tryExpression,
    jumpExpression,
    whenExpression,
    superExpression,
    objectDeclaration,
    typeAlias,
    postfixUnarySuffix,
    postfixUnaryOperator,
    navigationSuffix,
    callSuffix,
    annotatedLambda,
    lambdaLiteral,
    lambdaParameter,
    valueArguments,
    valueArgument,
    simpleIdentifier,
    literalConstant,
    primaryExpression,
    assignableSuffix,
    typeArguments,
    indexingSuffix,
    modifiers,
    memberAccessOperator,
    type,
    semi,
    label,
    annotation,
    identifierTerminal("Identifier"),
    asterisk("MULT"),
    identifier,
    dot("DOT"),
    colonColon("COLONCOLON"),
    safeNav,
    asKeyword("AS"),
    varKeyword("VAR"),
    classKeyword("CLASS"),
    integerLiteral("IntegerLiteral"),
    longLiteral("LongLiteral"),
    hexLiteral("HexLiteral"),
    binLiteral("BinLiteral"),
    characterLiteral("CharacterLiteral"),
    unsignedLiteral("UnsignedLiteral"),
    realLiteral("RealLiteral"),
    booleanLiteral("BooleanLiteral"),
    stringLiteral,
    callableReference,
    lineStringLiteral,
    multiLineStringLiteral,
    multiLineStringText("MultiLineStrText"),
    lineStringText("LineStrText"),
    lineCommentTerminal("LineComment"),
    delimitedCommentTerminal("DelimitedComment"),
    endOfFile("EOF"),
    newline("NL"),
    whitespace("WS"),
    other;

    val astName: String = astDescription ?: name
}