package demo.demoSimple

import com.example.demoSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.*

object SimpleAnalysisDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        val schema = demoSchema()

        printResolutionResults(
            schema.resolve(
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

object BuilderFunctionsDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        val schema = demoSchema()
        
        printResolutionResults(
            schema.resolve(
                """
                    import com.example.C
                    
                    val c = C(1)
                    c.d(newD("one"))
                    
                    com.example.C(2).d(newD("two"))
                """.trimIndent()
            )
        )
    }
}