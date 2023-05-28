package com.h0tk3y.kotlin.staticObjectNotation

import kotlinx.ast.common.ast.Ast

sealed interface LanguageTreeElement {
    val originAst: Ast
} 

sealed interface FunctionArgument : LanguageTreeElement {
    data class Positional(val expr: Expr, override val originAst: Ast) : FunctionArgument
    data class Named(val name: String, val expr: Expr, override val originAst: Ast) : FunctionArgument
}

sealed interface Expr : LanguageTreeElement
sealed interface DataStatement : LanguageTreeElement

data class Block(val statement: DataStatement)

data class AccessChain(val nameParts: List<String>, override val originAst: Ast) : Expr
data class FunctionCall(val accessChain: AccessChain, val args: List<FunctionArgument>, override val originAst: Ast) : Expr, DataStatement
data class Assignment(val lhs: AccessChain, val rhs: Expr)
data class LocalValue(val name: String, val rhs: Expr)
sealed interface Literal : Expr {
    data class StringLiteral(val value: String, override val originAst: Ast) : Literal
    data class IntLiteral(val value: Int, override val originAst: Ast) : Literal
}
