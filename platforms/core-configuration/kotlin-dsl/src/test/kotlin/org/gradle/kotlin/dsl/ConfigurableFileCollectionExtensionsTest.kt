package org.gradle.kotlin.dsl

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.kotlin.doAnswer
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

    @Test
    fun `plusAssign falls back to plus when used with FileCollection var`() {
        val orig = mock<ConfigurableFileCollection> {
            on { plus(org.mockito.kotlin.any()) }.doAnswer { mock<FileCollection>() }
        }

        val b = mock<FileCollection>()

        var a: FileCollection = orig
        a += b

        verify(orig).plus(b)
        assertThat(a, not(sameInstance(orig)))
    }

    @Test
    fun `plusAssign modifies ConfigurableFileCollection in-place`() {
        val orig = mock<ConfigurableFileCollection>()
        val b = mock<FileCollection>()

        @Suppress("CanBeVal")
        var a = orig
        a += b

        verify(orig).from(b)
        assertThat(a, sameInstance(orig))
    }
}
