package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.junit.Test


@Suppress("deprecation")
class ProjectExtensionsTest {

    @Test
    fun `can get generic project extension by type`() {

        val project = mock<Project>()
        val extensions = mock<org.gradle.api.plugins.ExtensionContainer>()
        val extension = mock<NamedDomainObjectContainer<List<String>>>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(project.extensions)
            .thenReturn(extensions)
        whenever(extensions.findByType(eq(extensionType)))
            .thenReturn(extension)

        project.the<NamedDomainObjectContainer<List<String>>>()

        inOrder(extensions) {
            verify(extensions).findByType(eq(extensionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can configure generic project extension by type`() {

        val project = mock<Project>()
        val extensions = mock<org.gradle.api.plugins.ExtensionContainer>()
        val extension = mock<NamedDomainObjectContainer<List<String>>>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(project.extensions)
            .thenReturn(extensions)
        whenever(extensions.findByType(eq(extensionType)))
            .thenReturn(extension)

        project.configure<NamedDomainObjectContainer<List<String>>> {}

        inOrder(extensions) {
            verify(extensions).findByType(eq(extensionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun container() {

        val project = mock<Project> {
            on { container(any<Class<String>>()) } doReturn mock<NamedDomainObjectContainer<String>>()
            on { container(any<Class<String>>(), any<NamedDomainObjectFactory<String>>()) } doReturn mock<NamedDomainObjectContainer<String>>()
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
