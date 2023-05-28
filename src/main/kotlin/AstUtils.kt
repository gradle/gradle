package com.h0tk3y.kotlin.staticObjectNotation

import kotlinx.ast.common.ast.Ast
import kotlinx.ast.common.ast.AstNode
import kotlinx.ast.common.ast.AstTerminal

fun Ast.flatten(): Ast = traverse().firstOrNull { it is AstTerminal || it is AstNode && it.children.size > 1 } ?: this

fun Ast.findChild(predicate: (Ast) -> Boolean): Ast? = traverse().find { it != this && predicate(it) }

fun Ast.findSingleChild(predicate: (Ast) -> Boolean): Ast? = traverse().singleOrNull { it != this && predicate(it) }

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