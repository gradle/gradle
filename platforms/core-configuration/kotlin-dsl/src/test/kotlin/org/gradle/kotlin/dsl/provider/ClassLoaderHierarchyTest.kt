package org.gradle.kotlin.dsl.provider

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock

import org.gradle.api.internal.initialization.AbstractClassLoaderScope

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.classEntriesFor

import org.gradle.kotlin.dsl.support.zipTo

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.lang.ClassLoader.getSystemClassLoader


class ClassLoaderHierarchyTest : TestWithTempFiles() {

    @Test
    fun `can dump complete ClassLoader hierarchy to json`() {

        ChildFirstClassLoader(getSystemClassLoader(), classPathWith(DeepThought::class.java)).use { loader ->

            val `class` = loader.loadClass(DeepThought::class.qualifiedName)

            val targetScope = mock<AbstractClassLoaderScope> {
                on { localClassLoader } doReturn loader
                on { exportClassLoader } doReturn loader.parent
                on { parent }.then { it.mock }
                on { path }.then { "the path" }
            }

            val json = classLoaderHierarchyJsonFor(`class`, targetScope)

            val mapper = jacksonObjectMapper()
            val hierarchy = mapper.readValue<ClassLoaderHierarchy>(json)

            assertThat(hierarchy.classLoaders.size, equalTo(3))
            assertThat(hierarchy.scopes.size, equalTo(1))
            assertThat(hierarchy.classLoaders[0].parents, hasItem(hierarchy.classLoaders[1].id))
            assertThat(hierarchy.scopes[0].label, equalTo("the path"))
            assertThat(hierarchy.scopes[0].localClassLoader, equalTo(hierarchy.classLoaders[0].id))
            assertThat(hierarchy.scopes[0].exportClassLoader, equalTo(hierarchy.classLoaders[1].id))
        }
    }

    private
    fun classPathWith(`class`: Class<*>): ClassPath {
        val jar = file("fixture.jar")
        zipTo(jar, classEntriesFor(`class`))
        return DefaultClassPath.of(listOf(jar))
    }

    data class ClassLoaderHierarchy(
        val classLoaders: List<ClassLoaderNode>,
        val scopes: List<ScopeNode>
    )

    data class ClassLoaderNode(
        val id: String,
        val label: String,
        val parents: Set<String>,
        val classPath: List<String>
    )

    data class ScopeNode(
        val label: String,
        val localClassLoader: String,
        val exportClassLoader: String,
        val isLocked: Boolean
    )
}
