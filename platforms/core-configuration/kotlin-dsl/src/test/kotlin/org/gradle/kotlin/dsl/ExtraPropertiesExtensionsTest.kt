package org.gradle.kotlin.dsl

import org.gradle.api.plugins.ExtraPropertiesExtension
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify


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

    @Test
    fun `can initialize extra property to null via delegate provider`() {

        val extra = mock<ExtraPropertiesExtension> {
            on { get("property") } doReturn (null as Int?)
        }

        run {
            val property by extra<Int?>(null)

            // property is set eagerly
            verify(extra).set("property", null)

            // And to prove the type is inferred correctly
            use(property)
        }

        run {
            val property: Int? by extra
            inOrder(extra) {
                verify(extra).get("property")
                verifyNoMoreInteractions()
            }
            assertThat(property, nullValue())
        }
    }

    @Test
    fun `can initialize extra property to null using lambda expression`() {

        val extra = mock<ExtraPropertiesExtension>()

        run {
            val property by extra { null }

            // property is set eagerly
            verify(extra).set("property", null)

            // And to prove the type is inferred correctly
            use(property)
        }

        run {
            val property: Int? by extra
            inOrder(extra) {
                verify(extra).get("property")
                verifyNoMoreInteractions()
            }
            assertThat(property, nullValue())
        }
    }

    private
    fun use(@Suppress("UNUSED_PARAMETER") property: Int?) = Unit
}
