package org.gradle.kotlin.dsl.accessors

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.classEntriesFor
import org.gradle.kotlin.dsl.support.zipTo

import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Test

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import java.io.File

import kotlin.reflect.KClass

@Suppress("unused")
class PublicGenericType<T>
class PublicComponentType
private class PrivateComponentType

class ProjectSchemaTest : TestWithTempFiles() {

    @Test
    fun `#isLegalExtensionName rejects illegal Kotlin extension names`() {

        assert(isLegalExtensionName("foo_bar"))
        assert(isLegalExtensionName("foo-bar"))
        assert(isLegalExtensionName("foo bar"))

        assertFalse(isLegalExtensionName("foo`bar"))
        assertFalse(isLegalExtensionName("foo.bar"))
        assertFalse(isLegalExtensionName("foo/bar"))
        assertFalse(isLegalExtensionName("foo\\bar"))
    }

    @Test
    fun `non existing type is represented as Inaccessible because NonAvailable`() {

        val typeString = "non.existing.Type"

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to typeString),
            ClassPath.EMPTY)

        assertThat(
            projectSchema.extensions["buildScan"]!!,
            equalTo(inaccessible(typeString, nonAvailable(typeString))))
    }

    @Test
    fun `public type is represented as Accessible`() {

        val typeString = "existing.Type"

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to typeString),
            classPathWithPublicType(typeString))

        assertThat(
            projectSchema.extensions["buildScan"]!!,
            equalTo(accessible(typeString)))
    }

    @Test
    fun `private type is represented as Inaccessible because NonPublic`() {

        val typeString = "non.visible.Type"

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to typeString),
            classPathWithPrivateType(typeString))

        assertThat(
            projectSchema.extensions["buildScan"]!!,
            equalTo(inaccessible(typeString, nonPublic(typeString))))
    }

    @Test
    fun `public synthetic type is represented as Inaccessible because Synthetic`() {

        val typeString = "synthetic.Type"

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to typeString),
            classPathWithType(typeString, ACC_PUBLIC, ACC_SYNTHETIC))

        assertThat(
            projectSchema.extensions["buildScan"]!!,
            equalTo(inaccessible(typeString, synthetic(typeString))))
    }

    @Test
    fun `parameterized public type with public component type is represented as Accessible`() {

        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericTypeString),
            classPathWith(PublicGenericType::class, PublicComponentType::class))

        assertThat(
            projectSchema.extensions["generic"]!!,
            equalTo(accessible(genericTypeString)))
    }

    @Test
    fun `parameterized public type with non public component type is represented as Inaccessible because of NonPublic component type`() {

        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PrivateComponentType>>())

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericTypeString),
            classPathWith(PublicGenericType::class, PrivateComponentType::class))

        assertThat(
            projectSchema.extensions["generic"]!!,
            equalTo(inaccessible(genericTypeString, nonPublic(PrivateComponentType::class.qualifiedName!!))))
    }

    @Test
    fun `parameterized public type with non existing component type is represented as Inaccessible because of NonAvailable component type`() {

        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericTypeString),
            classPathWith(PublicGenericType::class))

        assertThat(
            projectSchema.extensions["generic"]!!,
            equalTo(inaccessible(genericTypeString, nonAvailable(PublicComponentType::class.qualifiedName!!))))
    }

    @Test
    fun `public type from jar is represented as Accessible`() {

        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericTypeString),
            jarClassPathWith(PublicGenericType::class, PublicComponentType::class))

        assertThat(
            projectSchema.extensions["generic"]!!,
            equalTo(accessible(genericTypeString)))
    }

    private
    fun jarClassPathWith(vararg classes: KClass<*>): ClassPath =
        classPathOf(file("cp.jar").also { jar ->
            zipTo(jar, classEntriesFor(*classes.map { it.java }.toTypedArray()))
        })

    private
    fun classPathWith(vararg classes: KClass<*>): ClassPath =
        classPathOf(file("cp").also { rootDir ->
            for ((path, bytes) in classEntriesFor(*classes.map { it.java }.toTypedArray())) {
                File(rootDir, path).apply {
                    parentFile.mkdirs()
                    writeBytes(bytes)
                }
            }
        })

    private
    fun classPathWithPublicType(name: String) =
        classPathWithType(name, ACC_PUBLIC)

    private
    fun classPathWithPrivateType(name: String) =
        classPathWithType(name, ACC_PRIVATE)

    private
    fun classPathWithType(name: String, vararg modifiers: Int): ClassPath =
        classPathOf(file("cp").also { rootDir ->
            classFileForType(name, rootDir, *modifiers)
        })

    private
    fun classPathOf(vararg files: File) =
        DefaultClassPath.of(files.asList())

    private
    fun classFileForType(name: String, rootDir: File, vararg modifiers: Int) {
        File(rootDir, "${name.replace(".", "/")}.class").apply {
            parentFile.mkdirs()
            writeBytes(classBytesOf(name, *modifiers))
        }
    }

    private
    fun schemaWithExtensions(vararg pairs: Pair<String, String>) =
        ProjectSchema(
            extensions = mapOf(*pairs),
            conventions = emptyMap(),
            configurations = emptyList())


    private
    fun classBytesOf(name: String, vararg modifiers: Int): ByteArray =
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
}
