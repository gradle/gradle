package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.junit.Test


@Suppress("deprecation")
class ProjectExtensionsTest {

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
