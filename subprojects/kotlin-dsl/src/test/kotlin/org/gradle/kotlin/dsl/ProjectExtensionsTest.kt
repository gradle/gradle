package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.plugins.Convention
import org.gradle.api.reflect.TypeOf

import org.junit.Assert.fail
import org.junit.Test


@Suppress("deprecation")
class ProjectExtensionsTest {

    @Test
    fun `can get generic project extension by type`() {

        val project = mock<Project>()
        val convention = mock<Convention>()
        val extension = mock<NamedDomainObjectContainer<List<String>>>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(project.convention)
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
        val convention = mock<Convention>()
        val extension = mock<NamedDomainObjectContainer<List<String>>>()
        val extensionType = typeOf<NamedDomainObjectContainer<List<String>>>()

        whenever(project.convention)
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
        val convention = mock<Convention>()
        val javaConvention = mock<org.gradle.api.plugins.JavaPluginConvention>()

        whenever(project.convention)
            .thenReturn(convention)
        whenever(convention.findPlugin(eq(org.gradle.api.plugins.JavaPluginConvention::class.java)))
            .thenReturn(javaConvention)

        project.the<org.gradle.api.plugins.JavaPluginConvention>()

        inOrder(convention) {
            verify(convention).findByType(any<TypeOf<org.gradle.api.plugins.JavaPluginConvention>>())
            verify(convention).findPlugin(eq(org.gradle.api.plugins.JavaPluginConvention::class.java))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can configure convention by type`() {

        val project = mock<Project>()
        val convention = mock<Convention>()
        val javaConvention = mock<org.gradle.api.plugins.JavaPluginConvention>()

        whenever(project.convention)
            .thenReturn(convention)
        whenever(convention.findByType(any<TypeOf<*>>()))
            .thenReturn(null)
        whenever(convention.findPlugin(eq(org.gradle.api.plugins.JavaPluginConvention::class.java)))
            .thenReturn(javaConvention)

        project.configure<org.gradle.api.plugins.JavaPluginConvention> {}

        inOrder(convention) {
            verify(convention).findByType(any<TypeOf<org.gradle.api.plugins.JavaPluginConvention>>())
            verify(convention).findPlugin(eq(org.gradle.api.plugins.JavaPluginConvention::class.java))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `the() falls back to throwing getByType when not found`() {

        val project = mock<Project>()
        val convention = mock<Convention>()
        val conventionType = typeOf<org.gradle.api.plugins.JavaPluginConvention>()

        whenever(project.convention)
            .thenReturn(convention)
        whenever(convention.getByType(eq(conventionType)))
            .thenThrow(UnknownDomainObjectException::class.java)

        try {
            project.the<org.gradle.api.plugins.JavaPluginConvention>()
            fail("UnknownDomainObjectException not thrown")
        } catch (ex: UnknownDomainObjectException) {
            // expected
        }

        inOrder(convention) {
            verify(convention).findByType(eq(conventionType))
            verify(convention).findPlugin(eq(org.gradle.api.plugins.JavaPluginConvention::class.java))
            verify(convention).getByType(eq(conventionType))
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `configure() falls back to throwing configure when not found`() {

        val project = mock<Project>()
        val convention = mock<Convention>()
        val conventionType = typeOf<org.gradle.api.plugins.JavaPluginConvention>()

        whenever(project.convention)
            .thenReturn(convention)
        whenever(convention.configure(eq(conventionType), any<Action<org.gradle.api.plugins.JavaPluginConvention>>()))
            .thenThrow(UnknownDomainObjectException::class.java)

        try {
            project.configure<org.gradle.api.plugins.JavaPluginConvention> {}
            fail("UnknownDomainObjectException not thrown")
        } catch (ex: UnknownDomainObjectException) {
            // expected
        }

        inOrder(convention) {
            verify(convention).findByType(eq(conventionType))
            verify(convention).findPlugin(eq(org.gradle.api.plugins.JavaPluginConvention::class.java))
            verify(convention).configure(eq(conventionType), any<Action<org.gradle.api.plugins.JavaPluginConvention>>())
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
