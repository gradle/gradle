package org.gradle.kotlin.dsl

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.test.fixtures.ExpectDeprecationExtension

import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify


class ConfigurableFileCollectionExtensionsTest {

    @Test
    fun `assignment to delegated property means #setFrom`() {
        ExpectDeprecationExtension.intercept(
            "The 'val files by configurableFileCollection; files = ...' property delegate syntax has been deprecated."
        ) {

            val fileCollection = mock<ConfigurableFileCollection>()

            @Suppress("DEPRECATION")
            var delegatedProperty by fileCollection

            val value = mock<ConfigurableFileCollection>()
            delegatedProperty = value

            verify(fileCollection).setFrom(value)
            assertThat(delegatedProperty, sameInstance(fileCollection))
        }
    }
}
