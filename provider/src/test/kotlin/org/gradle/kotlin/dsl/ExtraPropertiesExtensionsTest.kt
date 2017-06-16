package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.plugins.ExtraPropertiesExtension

import org.junit.Test


class ExtraPropertiesExtensionsTest {

    @Test
    fun `can initialize extra property via delegate provider`() {

        val extra = mock<ExtraPropertiesExtension> {
            on { get("property") } doReturn 42
        }

        val property by extra(42)

        // property is set eagerly
        verify(extra).set("property", 42)

        // And to prove the type is inferred correctly
        use(property)
    }

    @Test
    fun `can initialize extra property using lambda expression`() {

        val extra = mock<ExtraPropertiesExtension> {
            on { get("property") } doReturn 42
        }

        val property by extra { 42 }

        // property is set eagerly
        verify(extra).set("property", 42)

        // And to prove the type is inferred correctly
        use(property)
    }

    private
    fun use(@Suppress("unused_parameter") property: Int) = Unit
}
