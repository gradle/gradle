package org.gradle.kotlin.dsl.accessors

import org.junit.Assert.assertTrue
import org.gradle.internal.classpath.ClassPath

import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


internal
interface InternalType


class TypeAccessibilityProviderTest : TestWithClassPath() {

    @Test
    fun `public generic type with primitive component type is accessible`() {

        val genericTypeWithPrimitiveComponent = SchemaType.of<PublicGenericType<String>>()
        assertThat(
            accessibilityFor(
                genericTypeWithPrimitiveComponent,
                classPath = jarClassPathWith(PublicGenericType::class)
            ),
            equalTo(accessible(genericTypeWithPrimitiveComponent))
        )
    }

    @Test
    fun `internal Kotlin type is inaccessible because NonPublic`() {

        val internalType = SchemaType.of<InternalType>()
        assertThat(
            accessibilityFor(
                internalType,
                classPath = jarClassPathWith(InternalType::class)
            ),
            equalTo(inaccessible(internalType, InaccessibilityReason.NonPublic(internalType.kotlinString)))
        )
    }

    @Test
    fun `class names from type strings`() {

        classNamesFromTypeString("String").apply {
            assertTrue(all.isEmpty())
            assertTrue(leaves.isEmpty())
        }

        classNamesFromTypeString("CustomTask").apply {
            assertThat(all, hasItems("CustomTask"))
            assertThat(leaves, hasItems("CustomTask"))
        }

        classNamesFromTypeString("java.util.List<String>").apply {
            assertThat(all, hasItems("java.util.List"))
            assertTrue(leaves.isEmpty())
        }

        classNamesFromTypeString("org.gradle.api.NamedDomainObjectContainer<Extension>").apply {
            assertThat(all, hasItems("org.gradle.api.NamedDomainObjectContainer", "Extension"))
            assertThat(leaves, hasItems("Extension"))
        }

        classNamesFromTypeString("java.lang.String").apply {
            assertThat(all, hasItems("java.lang.String"))
            assertThat(leaves, hasItems("java.lang.String"))
        }

        classNamesFromTypeString("java.util.Map<java.util.List, java.util.Set>").apply {
            assertThat(all, hasItems("java.util.Map", "java.util.List", "java.util.Set"))
            assertThat(leaves, hasItems("java.util.List", "java.util.Set"))
        }
    }

    private
    fun accessibilityFor(type: SchemaType, classPath: ClassPath) =
        TypeAccessibilityProvider(classPath).use {
            it.accessibilityForType(type)
        }
}
