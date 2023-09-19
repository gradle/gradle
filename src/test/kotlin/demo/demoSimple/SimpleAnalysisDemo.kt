package demo.demoSimple

import com.example.*
import com.example.demoSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
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