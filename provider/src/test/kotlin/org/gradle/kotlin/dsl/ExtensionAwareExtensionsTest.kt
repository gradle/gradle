package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

import org.junit.Test


class ExtensionAwareExtensionsTest {

    @Test
    fun `can configure task extensions`() {

        val extensionType = JacocoTaskExtension::class.java

        val extensionContainer = mock<ExtensionContainer>()

        val task = mock<Task> {
            on { extensions } doReturn extensionContainer
        }

        task.the<JacocoTaskExtension>()
        verify(extensionContainer).getByType(eq(extensionType))

        task.configure<JacocoTaskExtension> {}
        verify(extensionContainer).configure(eq(extensionType), any())
    }
}
