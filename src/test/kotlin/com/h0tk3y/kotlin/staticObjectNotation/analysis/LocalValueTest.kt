package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.example.com.h0tk3y.kotlin.staticObjectNotation.analysis.MyClass
import com.example.com.h0tk3y.kotlin.staticObjectNotation.analysis.TopLevel
import com.h0tk3y.kotlin.staticObjectNotation.demo.resolve
import com.h0tk3y.kotlin.staticObjectNotation.language.PropertyAccess
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

val schema =
    schemaFromTypes(TopLevel::class, listOf(TopLevel::class, MyClass::class), emptyList(), emptyMap(), emptyList())

object LocalValueTest {
    @Test
    fun `local values with the same name are not ambiguous`() {
        val resolution = schema.resolve(
            """
            val m = my1()

            my {
                my = m
            }

            my {
                val m = my2()
                my = m
            }
            """.trimIndent()
        )

        val rhsOrigins = resolution.assignments.map { it.rhs as ObjectOrigin.FromLocalValue }
        val (rhs1, rhs2) = rhsOrigins

        with(rhs1.assigned) {
            assertIs<ObjectOrigin.NewObjectFromMemberFunction>(this)
            assertEquals("my1", function.simpleName)
        }

        with(rhs2.assigned) {
            assertIs<ObjectOrigin.NewObjectFromMemberFunction>(this)
            assertEquals("my2", function.simpleName)
        }
    }

    @Test
    fun `a local value cannot be used until assigned at top level`() {
        val resolution = schema.resolve(
            """
            my {
                my = m
            }

            val m = my1()

            my {
                my = m
            }
            """.trimIndent()
        )

        val errorReasons = resolution.errors.map { it.errorReason }
        assertEquals(2, errorReasons.size)
        assertTrue { ErrorReason.UnresolvedAssignmentRhs in errorReasons }
        assertTrue {
            errorReasons.any {
                with(it) {
                    this is ErrorReason.UnresolvedReference && reference.run {
                        this is PropertyAccess && name == "m"
                    }
                }
            }
        }
    }
}
