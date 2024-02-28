package org.gradle.internal.declarativedsl.demo.demoSimple

import org.gradle.internal.declarativedsl.demo.printResolutionResults
import org.gradle.internal.declarativedsl.demo.printResolvedAssignments
import org.gradle.internal.declarativedsl.demo.resolve


object AssignmentResolverDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        val resolution = schema.resolve(
            """
            val myD = newD("shared")

            val c1 = c(1)
            c1.d = myD
            str = c1.d.id
            """.trimIndent()
        )
        printResolutionResults(resolution)
        printResolvedAssignments(resolution)
    }
}
