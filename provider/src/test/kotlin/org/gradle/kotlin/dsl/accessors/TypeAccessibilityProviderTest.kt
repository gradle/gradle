package org.gradle.kotlin.dsl.accessors

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.provider.spi.kotlinTypeStringFor
import org.gradle.kotlin.dsl.typeOf

import org.hamcrest.CoreMatchers.*

import org.junit.Assert.*
import org.junit.Test


class TypeAccessibilityProviderTest : TestWithClassPath() {

    @Test
    fun `public generic type with primitive component type is accessible`() {

        val genericTypeWithPrimitiveComponent = kotlinTypeStringFor(typeOf<PublicGenericType<String>>())
        assertThat(
            accessibilityFor(
                genericTypeWithPrimitiveComponent,
                jarClassPathWith(PublicGenericType::class)),
            equalTo(accessible(genericTypeWithPrimitiveComponent)))
    }

    @Test
    fun `class names from type strings`() {

        classNamesFromTypeString("String").apply {
            assertTrue(all.isEmpty())
            assertTrue(leafs.isEmpty())
        }

        classNamesFromTypeString("java.util.List<String>").apply {
            assertThat(all, hasItems("java.util.List"))
            assertTrue(leafs.isEmpty())
        }

        classNamesFromTypeString("java.lang.String").apply {
            assertThat(all, hasItems("java.lang.String"))
            assertThat(leafs, hasItems("java.lang.String"))
        }

        classNamesFromTypeString("java.util.Map<java.util.List, java.util.Set>").apply {
            assertThat(all, hasItems("java.util.Map", "java.util.List", "java.util.Set"))
            assertThat(leafs, hasItems("java.util.List", "java.util.Set"))
        }
    }

    private
    fun accessibilityFor(type: String, classPath: ClassPath) =
        TypeAccessibilityProvider(classPath).use {
            it.accessibilityForType(type)
        }
}
