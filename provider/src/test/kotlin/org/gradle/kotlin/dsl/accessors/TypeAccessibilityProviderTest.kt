package org.gradle.kotlin.dsl.accessors

import org.gradle.internal.classpath.ClassPath

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

    private
    fun accessibilityFor(type: String, classPath: ClassPath) =
        TypeAccessibilityProvider(classPath).use {
            it.accessibilityForType(type)
        }
}
