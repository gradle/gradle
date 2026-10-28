package org.gradle.kotlin.dsl

import org.gradle.api.specs.Spec
import org.junit.Assert.assertTrue
import org.junit.Test


class SpecExtensionsTest {

    @Test
    fun `can use function invocation syntax on Spec instances`() {

        val spec = Spec<Boolean> { it }
        assertTrue(spec(true))
    }
}
