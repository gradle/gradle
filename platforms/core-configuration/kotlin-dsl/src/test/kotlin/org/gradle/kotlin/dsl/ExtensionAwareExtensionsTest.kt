package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

import org.junit.Test


class ExtensionAwareExtensionsTest {

    interface MyExtension

    @Test
    fun `can access gradle extensions`() {

        val gradle = mock<Gradle>()

        val extensionContainer = mock<ExtensionContainer>()
        val extension = mock<MyExtension>()
        val extensionType = typeOf<MyExtension>()

        whenever(gradle.extensions)
            .thenReturn(extensionContainer)
        whenever(extensionContainer.getByType(eq(extensionType)))
            .thenReturn(extension)

        gradle.the<MyExtension>()
        gradle.configure<MyExtension> {}

        inOrder(extensionContainer) {
            verify(extensionContainer).getByType(eq(extensionType))
            verify(extensionContainer).configure(eq(extensionType), any<Action<MyExtension>>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can get task extensions`() {

        val task = mock<Task>()
        val extensionContainer = mock<ExtensionContainer>()
        val extension = mock<JacocoTaskExtension>()
        val extensionType = typeOf<JacocoTaskExtension>()

        whenever(task.extensions)
            .thenReturn(extensionContainer)
        whenever(extensionContainer.getByType(eq(extensionType)))
            .thenReturn(extension)

        task.the<JacocoTaskExtension>()

        inOrder(extensionContainer) {
            verify(extensionContainer).getByType(eq(extensionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can configure task extensions`() {

        val task = mock<Task>()
        val extensionContainer = mock<ExtensionContainer>()
        val extensionType = typeOf<JacocoTaskExtension>()

        whenever(task.extensions)
            .thenReturn(extensionContainer)

        task.configure<JacocoTaskExtension> {}

        inOrder(extensionContainer) {
            verify(extensionContainer).configure(eq(extensionType), any<Action<JacocoTaskExtension>>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can get generic extension by type`() {

        val extensionAware = mock<ExtensionAware>()
        val extensions = mock<ExtensionContainer>()
        val extension = mock<NamedDomainObjectContainer<List<String>>>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(extensionAware.extensions)
            .thenReturn(extensions)
        whenever(extensions.getByType(eq(extensionType)))
            .thenReturn(extension)

        extensionAware.the<NamedDomainObjectContainer<List<String>>>()

        inOrder(extensions) {
            verify(extensions).getByType(eq(extensionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can configure generic extension by type`() {

        val extensionAware = mock<ExtensionAware>()
        val extensions = mock<ExtensionContainer>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(extensionAware.extensions)
            .thenReturn(extensions)

        extensionAware.configure<NamedDomainObjectContainer<List<String>>> {}

        inOrder(extensions) {
            verify(extensions).configure(eq(extensionType), any<Action<NamedDomainObjectContainer<List<String>>>>())
            verifyNoMoreInteractions()
        }
    }
}
