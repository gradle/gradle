package com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.demoSimple

import com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection.reflectAndPrint

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