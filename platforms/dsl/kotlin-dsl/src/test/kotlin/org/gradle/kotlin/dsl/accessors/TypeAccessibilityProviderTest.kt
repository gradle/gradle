package org.gradle.kotlin.dsl.accessors

import org.gradle.internal.classpath.ClassPath
import org.hamcrest.CoreMatchers.equalTo
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

    private
    fun accessibilityFor(type: SchemaType, classPath: ClassPath) =
        TypeAccessibilityProvider(classPath).use {
            it.accessibilityForType(type)
        }
}
