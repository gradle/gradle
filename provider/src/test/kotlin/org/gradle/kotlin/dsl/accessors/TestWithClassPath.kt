package org.gradle.kotlin.dsl.accessors

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.classEntriesFor
import org.gradle.kotlin.dsl.support.zipTo

import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*

import java.io.File

import kotlin.reflect.KClass


open class TestWithClassPath : TestWithTempFiles() {

    protected
    fun jarClassPathWith(vararg classes: KClass<*>): ClassPath =
        classPathOf(file("cp.jar").also { jar ->
            zipTo(jar, classEntriesFor(*classes.map { it.java }.toTypedArray()))
        })

    protected
    fun classPathWith(vararg classes: KClass<*>): ClassPath =
        classPathOf(file("cp").also { rootDir ->
            for ((path, bytes) in classEntriesFor(*classes.map { it.java }.toTypedArray())) {
                File(rootDir, path).apply {
                    parentFile.mkdirs()
                    writeBytes(bytes)
                }
            }
        })

    protected
    fun classPathWithPublicType(name: String) =
        classPathWithType(name, ACC_PUBLIC)

    protected
    fun classPathWithPrivateType(name: String) =
        classPathWithType(name, ACC_PRIVATE)

    protected
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
