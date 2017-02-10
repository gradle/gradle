package org.gradle.script.lang.kotlin.accessors

import org.gradle.api.reflect.TypeOf
import org.gradle.api.reflect.TypeOf.parameterizedTypeOf
import org.gradle.api.reflect.TypeOf.typeOf

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Assert.assertFalse
import org.junit.Test

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import java.lang.reflect.Array

class ProjectSchemaTest {

    @Test
    fun `#accessibleProjectSchemaFrom rejects non-public or synthetic types`() {

        assertThat(
            accessibleProjectSchemaFrom(
                extensionSchema = mapOf(
                    "publicNonSynthetic" to publicNonSyntheticType,
                    "nonPublic" to nonPublicType,
                    "synthetic" to syntheticType),
                conventionPlugins = mapOf(
                    "publicNonSyntheticInstance" to instanceOf(publicNonSyntheticClass),
                    "nonPublicInstance" to instanceOf(nonPublicClass),
                    "syntheticInstance" to instanceOf(syntheticClass))),
            equalTo(
                ProjectSchema(
                    extensions = mapOf("publicNonSynthetic" to publicNonSyntheticType),
                    conventions = mapOf("publicNonSyntheticInstance" to publicNonSyntheticType))))
    }

    @Test
    fun `#isAccessible rejects array of non-public or synthetic type`() {

        assert(isAccessible(arrayTypeOf(publicNonSyntheticClass)))
        assertFalse(isAccessible(arrayTypeOf(nonPublicClass)))
        assertFalse(isAccessible(arrayTypeOf(syntheticClass)))
    }

    @Test
    fun `#isAccessible rejects parameterized type of non-public or synthetic type`() {

        assert(isAccessible(listTypeOf(publicNonSyntheticType)))
        assertFalse(isAccessible(listTypeOf(nonPublicType)))
        assertFalse(isAccessible(listTypeOf(syntheticType)))
    }

    fun instanceOf(`class`: Class<*>): Any =
        `class`.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

    fun arrayTypeOf(componentType: Class<*>): TypeOf<*> =
        typeOf(Array.newInstance(componentType, 0).javaClass)

    fun listTypeOf(componentType: TypeOf<*>): TypeOf<*> =
        parameterizedTypeOf(object : TypeOf<List<*>>() {}, componentType)

    val publicNonSyntheticType by lazy {
        typeOf(publicNonSyntheticClass)!!
    }

    val publicNonSyntheticClass by lazy {
        defineClass("PublicNonSynthetic", ACC_PUBLIC)
    }

    val nonPublicType by lazy {
        typeOf(nonPublicClass)!!
    }

    val nonPublicClass by lazy {
        defineClass("NonPublic")
    }

    val syntheticType by lazy {
        typeOf(syntheticClass)!!
    }

    val syntheticClass by lazy {
        defineClass("Synthetic", ACC_PUBLIC, ACC_SYNTHETIC)
    }

    fun defineClass(name: String, vararg modifiers: Int): Class<*> =
        DynamicClassLoader().defineClass(name, classBytesOf(name, modifiers))

    fun classBytesOf(name: String, modifiers: IntArray): ByteArray =
        ClassWriter(0).run {
            visit(V1_7, modifiers.fold(0, Int::plus), name, null, "java/lang/Object", null)
            visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
                visitCode()
                visitVarInsn(ALOAD, 0)
                visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                visitInsn(RETURN)
                visitMaxs(1, 1)
            }
            visitEnd()
            toByteArray()
        }

    class DynamicClassLoader : ClassLoader() {
        fun defineClass(name: String, bytes: ByteArray): Class<*> =
            defineClass(name, bytes, 0, bytes.size)!!
    }
}
