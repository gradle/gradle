package org.gradle.kotlin.dsl.accessors

import org.gradle.api.reflect.TypeOf.typeOf

import org.gradle.kotlin.dsl.typeOf

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class KotlinTypeStringTest {

    @Test
    fun `#kotlinTypeStringFor array type`() {
        assertThat(
            kotlinTypeStringFor(typeOf<Array<String>>()),
            equalTo("Array<String>")
        )
    }

    @Test
    fun `#kotlinTypeStringFor parameterized type`() {
        assertThat(
            kotlinTypeStringFor(typeOf<List<Array<String>>>()),
            equalTo("java.util.List<Array<String>>")
        )
    }

    @Test
    fun `#kotlinTypeStringFor Kotlin function type`() {
        assertThat(
            kotlinTypeStringFor(typeOf<(String) -> String>()),
            equalTo("kotlin.jvm.functions.Function1<String, String>")
        )
    }

    @Test
    fun `#kotlinTypeStringFor Java function type`() {
        assertThat(
            kotlinTypeStringFor(typeOf<java.util.function.Function<String, String>>()),
            equalTo("java.util.function.Function<String, String>")
        )
    }

    @Test
    fun `#kotlinTypeStringFor primitive type`() {
        assertPrimitiveTypeName<Boolean>(java.lang.Boolean.TYPE)
        assertPrimitiveTypeName<Char>(java.lang.Character.TYPE)
        assertPrimitiveTypeName<Byte>(java.lang.Byte.TYPE)
        assertPrimitiveTypeName<Short>(java.lang.Short.TYPE)
        assertPrimitiveTypeName<Int>(java.lang.Integer.TYPE)
        assertPrimitiveTypeName<Long>(java.lang.Long.TYPE)
        assertPrimitiveTypeName<Float>(java.lang.Float.TYPE)
        assertPrimitiveTypeName<Double>(java.lang.Double.TYPE)
    }

    private
    inline fun <reified T> assertPrimitiveTypeName(primitiveTypeClass: Class<*>) {
        assertThat(
            kotlinTypeStringFor(typeOf(primitiveTypeClass)),
            equalTo(T::class.simpleName)
        )
        assertThat(
            kotlinTypeStringFor(typeOf<T>()),
            equalTo(T::class.simpleName)
        )
    }
}
