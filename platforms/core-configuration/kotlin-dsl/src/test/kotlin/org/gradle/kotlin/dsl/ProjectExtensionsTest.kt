package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.whenever
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.reflect.TypeOf
import org.junit.Assert.fail
import org.junit.Test


@Suppress("deprecation")
class ProjectExtensionsTest {

    abstract class CustomConvention

    @Test
    fun `can get generic project extension by type`() {

        val project = mock<Project>()
        val convention = mock<org.gradle.api.plugins.Convention>()
        val extension = mock<NamedDomainObjectContainer<List<String>>>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(project.extensions)
            .thenReturn(convention)
        whenever(convention.findByType(eq(extensionType)))
            .thenReturn(extension)

        project.the<NamedDomainObjectContainer<List<String>>>()

        inOrder(convention) {
            verify(convention).findByType(eq(extensionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can configure generic project extension by type`() {

        val project = mock<Project>()
        val convention = mock<org.gradle.api.plugins.Convention>()
        val extension = mock<NamedDomainObjectContainer<List<String>>>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(project.extensions)
            .thenReturn(convention)
        whenever(convention.findByType(eq(extensionType)))
            .thenReturn(extension)

        project.configure<NamedDomainObjectContainer<List<String>>> {}

        inOrder(convention) {
            verify(convention).findByType(eq(extensionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can get convention by type`() {

        val project = mock<Project>()
        val convention = mock<org.gradle.api.plugins.Convention>()
        val javaConvention = mock<CustomConvention>()

        whenever(project.extensions)
            .thenReturn(convention)
        whenever(project.convention)
            .thenReturn(convention)
        whenever(convention.findPlugin(eq(CustomConvention::class.java)))
            .thenReturn(javaConvention)

        project.the<CustomConvention>()

        inOrder(convention) {
            verify(convention).findByType(any<TypeOf<CustomConvention>>())
            verify(convention, times(2)).findPlugin(eq(CustomConvention::class.java))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can configure convention by type`() {

        val project = mock<Project>()
        val convention = mock<org.gradle.api.plugins.Convention>()
        val javaConvention = mock<CustomConvention>()

        whenever(project.extensions)
            .thenReturn(convention)
        whenever(project.convention)
            .thenReturn(convention)
        whenever(convention.findByType(any<TypeOf<*>>()))
            .thenReturn(null)
        whenever(convention.findPlugin(eq(CustomConvention::class.java)))
            .thenReturn(javaConvention)

        project.configure<CustomConvention> {}

        inOrder(convention) {
            verify(convention).findByType(any<TypeOf<CustomConvention>>())
            verify(convention, times(2)).findPlugin(eq(CustomConvention::class.java))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `the() falls back to throwing getByType when not found`() {

        val project = mock<Project>()
        val convention = mock<org.gradle.api.plugins.Convention>()
        val conventionType = typeOf<CustomConvention>()

        whenever(project.extensions)
            .thenReturn(convention)
        whenever(project.convention)
            .thenReturn(convention)
        whenever(convention.getByType(eq(conventionType)))
            .thenThrow(UnknownDomainObjectException::class.java)

        try {
            project.the<CustomConvention>()
            fail("UnknownDomainObjectException not thrown")
        } catch (ex: UnknownDomainObjectException) {
            // expected
        }

        inOrder(convention) {
            verify(convention).findByType(eq(conventionType))
            verify(convention).findPlugin(eq(CustomConvention::class.java))
            verify(convention).getByType(eq(conventionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `configure() falls back to throwing configure when not found`() {

        val project = mock<Project>()
        val convention = mock<org.gradle.api.plugins.Convention>()
        val conventionType = typeOf<CustomConvention>()

        whenever(project.extensions)
            .thenReturn(convention)
        whenever(project.convention)
            .thenReturn(convention)
        whenever(convention.configure(eq(conventionType), any<Action<CustomConvention>>()))
            .thenThrow(UnknownDomainObjectException::class.java)

        try {
            project.configure<CustomConvention> {}
            fail("UnknownDomainObjectException not thrown")
        } catch (ex: UnknownDomainObjectException) {
            // expected
        }

        inOrder(convention) {
            verify(convention).findByType(eq(conventionType))
            verify(convention).findPlugin(eq(CustomConvention::class.java))
            verify(convention).configure(eq(conventionType), any<Action<CustomConvention>>())
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
