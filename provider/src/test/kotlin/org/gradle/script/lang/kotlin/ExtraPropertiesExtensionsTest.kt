package org.gradle.script.lang.kotlin

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.plugins.ExtraPropertiesExtension

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class ExtraPropertiesExtensionsTest {

    @Test
    fun `can initialize extra property via delegate provider`() {

        val extra = mock<ExtraPropertiesExtension> {
            on { get("property") } doReturn 42
        }

        @Suppress("unused_variable")
        val property by extra(42)

        verify(extra).set("property", 42)

        // And to prove the type is inferred correctly
        assertThat(property / 2, equalTo(21))
    }
}
