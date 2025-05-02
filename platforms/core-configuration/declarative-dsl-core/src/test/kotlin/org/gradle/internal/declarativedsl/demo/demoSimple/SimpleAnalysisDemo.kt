package org.gradle.internal.declarativedsl.demo.demoSimple

import com.example.Abc
import com.example.C
import com.example.D
import com.example.newD
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.demo.printResolutionResults
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.FixedTopLevelFunctionDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes


val schema = schemaFromTypes(
    topLevelReceiver = Abc::class,
    types = listOf(Abc::class, C::class, D::class),
    externalFunctionDiscovery = FixedTopLevelFunctionDiscovery(listOf(::newD)),
    defaultImports = listOf(DefaultFqName("com.example", "newD"))
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
