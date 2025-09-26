package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever


class ProjectExtensionsTest {

    abstract class ExtensionType

    @Test
    fun `can get generic project extension by type`() {

        val project = mock<Project>()
        val extensionContainer = mock<org.gradle.api.plugins.ExtensionContainer>()
        val extension = mock<NamedDomainObjectContainer<List<String>>>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(project.extensions)
            .thenReturn(extensionContainer)
        whenever(extensionContainer.getByType(eq(extensionType)))
            .thenReturn(extension)

        project.the<NamedDomainObjectContainer<List<String>>>()

        inOrder(extensionContainer) {
            verify(extensionContainer).getByType(eq(extensionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can configure generic project extension by type`() {

        val project = mock<Project>()
        val extensionContainer = mock<org.gradle.api.plugins.ExtensionContainer>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(project.extensions)
            .thenReturn(extensionContainer)

        project.configure<NamedDomainObjectContainer<List<String>>> {}

        inOrder(extensionContainer) {
            verify(extensionContainer).configure(eq(extensionType), any<Action<NamedDomainObjectContainer<List<String>>>>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `the() falls back to throwing getByType when not found`() {

        val project = mock<Project>()
        val extensionContainer = mock<org.gradle.api.plugins.ExtensionContainer>()
        val extensionType = typeOf<ExtensionType>()

        whenever(project.extensions)
            .thenReturn(extensionContainer)
        whenever(extensionContainer.getByType(eq(extensionType)))
            .thenThrow(UnknownDomainObjectException::class.java)

        try {
            project.the<ExtensionType>()
            fail("UnknownDomainObjectException not thrown")
        } catch (_: UnknownDomainObjectException) {
            // expected
        }

        inOrder(extensionContainer) {
            verify(extensionContainer).getByType(eq(extensionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `configure() falls back to throwing configure when not found`() {

        val project = mock<Project>()
        val extensionContainer = mock<org.gradle.api.plugins.ExtensionContainer>()
        val extensionType = typeOf<ExtensionType>()

        whenever(project.extensions)
            .thenReturn(extensionContainer)
        whenever(extensionContainer.configure(eq(extensionType), any<Action<ExtensionType>>()))
            .thenThrow(UnknownDomainObjectException::class.java)

        try {
            project.configure<ExtensionType> {}
            fail("UnknownDomainObjectException not thrown")
        } catch (_: UnknownDomainObjectException) {
            // expected
        }

        inOrder(extensionContainer) {
            verify(extensionContainer).configure(eq(extensionType), any<Action<ExtensionType>>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun container() {

        val project = mock<Project> {
            on {
                container(any<Class<String>>())
            } doReturn mock<NamedDomainObjectContainer<String>>()
            on {
                container(any<Class<String>>(), any<NamedDomainObjectFactory<String>>())
            } doReturn mock<NamedDomainObjectContainer<String>>()
        }

        project.container<String>()

        inOrder(project) {
            verify(project).container(String::class.java)
            verifyNoMoreInteractions()
        }

        project.container { "some" }

        inOrder(project) {
            verify(project).container(any<Class<String>>(), any<NamedDomainObjectFactory<String>>())
            verifyNoMoreInteractions()
        }
    }
}
