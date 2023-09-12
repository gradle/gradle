package com.example

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*

class SimpleAnalysisDemo {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val schema = demoSchema()

            printResolutionResults(
                resolve(
                    schema,
                    """
                        val myB = b()
                        
                        a = myB
                        val myD = newD("shared")
                        
                        c(1) {
                            x = f(y)
                            d = myD
                        }
                        c(2) {
                            x = f("another test")
                            d = myD
                        }
                    """.trimIndent()
                )
            )
        }
    }
}
