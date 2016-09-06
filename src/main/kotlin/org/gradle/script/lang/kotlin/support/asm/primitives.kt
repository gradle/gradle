/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.script.lang.kotlin.support.asm

import org.gradle.script.lang.kotlin.codegen.ClassSignature
import org.gradle.script.lang.kotlin.codegen.MethodDescriptor
import org.gradle.script.lang.kotlin.codegen.ZipInputStreamEntry
import org.gradle.script.lang.kotlin.codegen.forEachZipEntryIn

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import java.io.InputStream
import java.io.OutputStream

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

typealias ZipInputStreamEntryPredicate = ZipInputStreamEntry.() -> Boolean

fun removeMethodsMatching(predicate: MethodPredicate,
                          input: InputStream,
                          output: OutputStream,
                          shouldTransformEntry: ZipInputStreamEntryPredicate = { zipEntry.name.endsWith(".class") },
                          onProgress: () -> Unit = {}) {
    transformClasses(input, output, shouldTransformEntry, onProgress) { classVisitor ->
        MethodRemovingVisitor(predicate, classVisitor)
    }
}

typealias MethodPredicate = (MethodDescriptor) -> Boolean

fun transformClasses(input: InputStream,
                     output: OutputStream,
                     shouldTransformEntry: ZipInputStreamEntryPredicate,
                     onProgress: () -> Unit,
                     decorator: (ClassVisitor) -> ClassVisitor) {
    ZipOutputStream(output).use { zos ->
        zos.setLevel(0)
        forEachZipEntryIn(input) {
            when {
                zipEntry.isDirectory -> {
                    zos.putNextEntry(ZipEntry(zipEntry))
                    zos.closeEntry()
                }
                else -> {
                    val resultingEntryBytes =
                        if (shouldTransformEntry()) transformClass(zipInputStream, decorator)
                        else zipInputStream.readBytes()
                    zos.putNextEntry(ZipEntry(zipEntry.name).apply {
                        time = zipEntry.time
                        size = resultingEntryBytes.size.toLong()
                    })
                    zos.write(resultingEntryBytes)
                    zos.closeEntry()
                }
            }
            onProgress()
        }
    }
}

fun transformClass(inputStream: InputStream, decorator: (ClassVisitor) -> ClassVisitor): ByteArray {
    val classReader = ClassReader(inputStream)
    val classWriter = ClassWriter(classReader, 0)
    classReader.accept(
        decorator(classWriter),
        ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES)
    return classWriter.toByteArray()
}

class MethodRemovingVisitor(
    val predicate: MethodPredicate,
    classVisitor: ClassVisitor) : ClassVisitor(Opcodes.ASM5, classVisitor) {

    private var nonGeneric = true

    override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
        super.visit(version, access, name, signature, superName, interfaces)
        nonGeneric = isNonGenericType(signature)
    }

    // TODO: move predicate out of this class
    private fun isNonGenericType(signature: String?) =
        signature?.let { ClassSignature.from(it).typeParameters.isEmpty() } ?: true

    override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
        return when {
            nonGeneric && predicate(MethodDescriptor(name, signature ?: desc, access)) -> null
            else -> super.visitMethod(access, name, desc, signature, exceptions)
        }
    }
}
