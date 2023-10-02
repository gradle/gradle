package com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.demoSimple

import com.h0tk3y.kotlin.staticObjectNotation.demo.printResolutionResults
import com.h0tk3y.kotlin.staticObjectNotation.demo.printResolvedAssignments
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve

object AssignmentResolverDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        val resolution = schema.resolve(
            """
            val myB = b()
            
            a = myB
            val myD = newD("shared")
            
            val c1 = c(1)
            c1.x = myB
            c1.d = myD
            
            c(2) {
                val cRef = c1
                x = cRef.x
                this.d = cRef.d
            }
            
            str = c1.d.id
            """.trimIndent()
        )
        printResolutionResults(resolution)
        printResolvedAssignments(resolution)
    }
}