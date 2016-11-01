package org.gradle.script.lang.kotlin

import org.junit.Test
import kotlin.test.assertEquals

class GroovyInteroperabilityTest {

    @Test
    fun `can use closure with single argument call`() {
        val list = arrayListOf<Int>()
        closureOf<MutableList<Int>> { add(42) }.call(list)
        assertEquals(42, list.first())
    }

    @Test
    fun `can use closure with delegate call`() {
        val list = arrayListOf<Int>()
        delegateClosureOf<MutableList<Int>> { add(42) }.apply {
            delegate = list
            call()
        }
        assertEquals(42, list.first())
    }

    @Test
    fun `can use KotlinClosure to control return type`() {
        fun stringClosure(function: String.() -> String) =
            KotlinClosure(function)

        assertEquals(
            "GROOVY",
            stringClosure { toUpperCase() }.call("groovy"))
    }
}
