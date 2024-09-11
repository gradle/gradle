package org.gradle.internal.declarativedsl.analysis

import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.language.PropertyAccess
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


val schema =
    schemaFromTypes(TopLevel::class, listOf(TopLevel::class, MyClass::class), emptyList(), emptyMap(), emptyList())


class LocalValueTest {
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
