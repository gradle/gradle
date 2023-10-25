package org.gradle.kotlin.dsl

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import kotlin.test.assertEquals


class PropertyExtensionsTest {

    val project: Project = ProjectBuilder.builder().build()

    @Test
    fun `plusAssign on ListProperty add a value`() {
        val property = project.objects.listProperty<String>()
        property.set(listOf("first"))

        property += "second"

        assertEquals(listOf("first", "second"), property.get())
    }

    @Test
    fun `plusAssign on ListProperty adds multiple values`() {
        val property = project.objects.listProperty<String>()
        property.set(listOf("first"))

        property += listOf("second", "third")

        assertEquals(listOf("first", "second", "third"), property.get())
    }

    @Test
    fun `plusAssign on ListProperty adds multiple values from provider`() {
        val property = project.objects.listProperty<String>()
        property.set(listOf("first"))

        property += project.provider { listOf("second", "third") }

        assertEquals(listOf("first", "second", "third"), property.get())
    }

    @Test
    fun `plusAssign on SetProperty add a value`() {
        val property = project.objects.setProperty<String>()
        property.set(listOf("first"))

        property += "second"

        assertEquals(setOf("first", "second"), property.get())
    }

    @Test
    fun `plusAssign on SetProperty adds multiple values`() {
        val property = project.objects.setProperty<String>()
        property.set(listOf("first"))

        property += listOf("second", "third")

        assertEquals(setOf("first", "second", "third"), property.get())
    }

    @Test
    fun `plusAssign on SetProperty adds multiple values from provider`() {
        val property = project.objects.setProperty<String>()
        property.set(listOf("first"))

        property += project.provider { listOf("second", "third") }

        assertEquals(setOf("first", "second", "third"), property.get())
    }

    @Test
    fun `plusAssign on MapProperty add a entry`() {
        val property = project.objects.mapProperty<String, Int>()
        property.set(mapOf("first" to 1))

        property += mapOf("second" to 2)

        assertEquals(mapOf("first" to 1, "second" to 2), property.get())
    }

    @Test
    fun `plusAssign on MapProperty adds multiple entries`() {
        val property = project.objects.mapProperty<String, Int>()
        property.set(mapOf("first" to 1))

        property += mapOf("second" to 2, "third" to 3)

        assertEquals(mapOf("first" to 1, "second" to 2, "third" to 3), property.get())
    }

    @Test
    fun `plusAssign on MapProperty adds multiple entries from provider`() {
        val property = project.objects.mapProperty<String, Int>()
        property.set(mapOf("first" to 1))

        property += project.provider { mapOf("second" to 2, "third" to 3) }

        assertEquals(mapOf("first" to 1, "second" to 2, "third" to 3), property.get())
    }
}
