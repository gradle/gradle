package com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.demoSimple

import com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection.reflectAndPrint
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ObjectOrigin
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaTypeRefContext
import com.h0tk3y.kotlin.staticObjectNotation.demo.assignmentTrace
import com.h0tk3y.kotlin.staticObjectNotation.demo.printReflection
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve
import com.h0tk3y.kotlin.staticObjectNotation.language.Null
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ReflectionContext
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.reflect
import kotlinx.ast.common.ast.AstAttachments
import kotlinx.ast.common.ast.DefaultAstNode

object ReflectionDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        schema.reflectAndPrint(
            """
            val myD = newD("shared")
            
            a = b()
            val c1 = c(1)
            c1.d = myD
            str = c1.d.id
            
            c(b()) {
                d = c1.d
            }
            """.trimIndent()
        )
    }
}