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

import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC

import java.io.File

import kotlin.reflect.KClass


open class TestWithClassPath : TestWithTempFiles() {

    protected
    fun jarClassPathWith(vararg classes: KClass<*>): ClassPath =
        jarClassPathWith("cp.jar", *classes)

    protected
    fun jarClassPathWith(path: String, vararg classes: KClass<*>): ClassPath = classPathOf(
        file(path).also { jar ->
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
fun TestWithTempFiles.classPathWith(builder: ClassPathBuilderScope.() -> Unit): ClassPath =
    classPathOf(newFolder().also { builder(ClassPathBuilderScope(it)) })


internal
class ClassPathBuilderScope(val outputDir: File)


internal
fun ClassPathBuilderScope.publicClass(name: String) {
    writeClassFileTo(outputDir, name, ACC_PUBLIC)
}


internal
fun ClassPathBuilderScope.publicInterface(name: String, vararg interfaces: String) {
    val internalName = InternalName.from(name)
    writeClassFileTo(
        outputDir,
        internalName,
        beginClass(
            ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE,
            internalName,
            interfaces = interfaces.takeIf { it.isNotEmpty() }?.map(InternalName.Companion::from)
        ).endClass()
    )
}


internal
fun classPathOf(vararg files: File) = DefaultClassPath.of(files.asList())


private
fun writeClassFileTo(rootDir: File, name: String, vararg modifiers: Int) {
    val internalName = InternalName.from(name)
    val classBytes = classBytesOf(modifiers.fold(0, Int::plus), internalName)
    writeClassFileTo(rootDir, internalName, classBytes)
}


private
fun writeClassFileTo(rootDir: File, className: InternalName, classBytes: ByteArray) {
    File(rootDir, "$className.class").apply {
        parentFile.mkdirs()
        writeBytes(classBytes)
    }
}


internal
fun classBytesOf(modifiers: Int, internalName: InternalName): ByteArray =
    beginClass(modifiers, internalName).run {
        publicDefaultConstructor()
        endClass()
    }
