package org.gradle.kotlin.dsl

import org.gradle.api.file.ConfigurableFileCollection

import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify


class ConfigurableFileCollectionExtensionsTest {

    @Test
    fun `assignment to delegated property means #setFrom`() {

        val fileCollection = mock<ConfigurableFileCollection>()
        var delegatedProperty by fileCollection

        val value = mock<ConfigurableFileCollection>()
        delegatedProperty = value

        verify(fileCollection).setFrom(value)
        assertThat(delegatedProperty, sameInstance(fileCollection))
    }
}
