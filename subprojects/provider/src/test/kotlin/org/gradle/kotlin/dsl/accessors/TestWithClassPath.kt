package org.gradle.kotlin.dsl.accessors

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.classEntriesFor
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.beginClass
import org.gradle.kotlin.dsl.support.bytecode.endClass
import org.gradle.kotlin.dsl.support.bytecode.publicDefaultConstructor
import org.gradle.kotlin.dsl.support.zipTo

import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC

import java.io.File

import kotlin.reflect.KClass


open class TestWithClassPath : TestWithTempFiles() {

    protected
    fun jarClassPathWith(vararg classes: KClass<*>): ClassPath = classPathOf(
        file("cp.jar").also { jar ->
            zipTo(jar, classEntriesFor(classes))
        }
    )

    protected
    fun classPathWith(vararg classes: KClass<*>): ClassPath = classPathOf(
        newFolder().also { rootDir ->
            for ((path, bytes) in classEntriesFor(classes)) {
                File(rootDir, path).apply {
                    parentFile.mkdirs()
                    writeBytes(bytes)
                }
            }
        }
    )
}


internal
fun TestWithTempFiles.classPathWithPublicType(name: String) =
    classPathWithType(name, ACC_PUBLIC)


internal
fun TestWithTempFiles.classPathWithPrivateType(name: String) =
    classPathWithType(name, ACC_PRIVATE)


internal
fun TestWithTempFiles.classPathWithType(name: String, vararg modifiers: Int): ClassPath = classPathOf(
    newFolder().also { rootDir ->
        writeClassFileTo(rootDir, name, *modifiers)
    }
)


internal
fun TestWithTempFiles.classPathWithPublicTypes(vararg names: String): ClassPath = classPathOf(
    newFolder().also { rootDir ->
        for (name in names) {
            writeClassFileTo(rootDir, name, ACC_PUBLIC)
        }
    }
)


internal
fun classPathOf(vararg files: File) = DefaultClassPath.of(files.asList())


private
fun writeClassFileTo(rootDir: File, name: String, vararg modifiers: Int) {
    val internalName = name.replace(".", "/")
    File(rootDir, "$internalName.class").apply {
        parentFile.mkdirs()
        writeBytes(classBytesOf(internalName, *modifiers))
    }
}


private
fun classBytesOf(name: String, vararg modifiers: Int): ByteArray =
    beginClass(modifiers.fold(0, Int::plus), InternalName(name)).run {
        publicDefaultConstructor()
        endClass()
    }
