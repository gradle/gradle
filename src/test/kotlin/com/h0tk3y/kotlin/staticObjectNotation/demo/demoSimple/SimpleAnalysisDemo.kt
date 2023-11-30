package com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.demoSimple

import com.example.*
import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.demo.printResolutionResults
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes

val schema = schemaFromTypes(
    topLevelReceiver = Abc::class,
    types = listOf(Abc::class, C::class, D::class),
    externalFunctions = listOf(::newD),
    defaultImports = listOf(FqName("com.example", "newD"))
)

object SimpleAnalysisDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        printResolutionResults(
            schema.resolve(
                """
                    val myB = b()

                    a = myB
                    val myD = newD("shared")

                    val c1 = c(1)
                    c1.x = c1.f(c1.y)
                    c1.d = myD

                    c(2) {
                        x = f("another test")
                        this.d = myD
                    }
                """.trimIndent()
            )
        )
    }
}

object BuilderFunctionsDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        printResolutionResults(
            schema.resolve(
                """
                    import com.example.C

                    c(1).d(newD("one"))

                    c(2) {
                        d(newD("two"))
                    }

                    c(3) {
                        d = newD("three")
                    }
                """.trimIndent()
            )
        )
    }
}
