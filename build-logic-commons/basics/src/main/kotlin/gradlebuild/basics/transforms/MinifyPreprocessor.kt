/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.basics.transforms

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream


/**
 * Removes packages or resources from a library jar.
 * R8 has limitation for some scenarios, so this is a workaround to remove packages before minification.
 */
object MinifyPreprocessor {

    private
    val multiReleaseVersionPrefix = Regex("""^META-INF/versions/(\d+)/""")

    private
    val signatureFile = Regex("""^META-INF/[^/]+\.(SF|RSA|DSA|EC)$""")

    fun preprocess(source: File, target: File, spec: MinifySpec, javaVersion: Int) {
        val methodsToErase = spec.erasedMethods.groupBy({ it.substringBefore('#').toEntryName() }, { it.substringAfter('#') })
        ZipFile(source).use { jar ->
            val removedPackages = spec.forceRemovePackages.map { "${it.toInternalName()}/" }
            val (removed, kept) = jar.entries().toList()
                .filterNot { it.isDirectory || it.name.withoutVersionPrefix() == "module-info.class" }
                .groupBy { it.name.withoutVersionPrefix() }
                .mapNotNull { (_, copies) -> copies.filter { it.javaVersion() <= javaVersion }.maxByOrNull { it.javaVersion() } }
                .partition { entry -> removedPackages.any { entry.name.withoutVersionPrefix().startsWith(it) } }
            check(removed.isNotEmpty() || removedPackages.isEmpty()) {
                "Nothing to remove from $source - the library changed, revisit the minify configuration"
            }
            ZipOutputStream(target.outputStream().buffered()).use { out ->
                kept.asSequence()
                    // A signed Jar states the digest of every entry, and this one is no longer that Jar.
                    .filterNot { signatureFile.matches(it.name) }
                    .forEach { entry ->
                        out.putNextEntry(ZipEntry(entry.name.withoutVersionPrefix()).apply { time = entry.time })
                        val bytes = jar.getInputStream(entry).readBytes()
                        val methodsToEraseInClass = methodsToErase[entry.name.withoutVersionPrefix()].orEmpty()
                        out.write(
                            if (methodsToEraseInClass.isEmpty()) {
                                bytes
                            } else {
                                eraseMethods(bytes, methodsToEraseInClass, entry.name, source)
                            }
                        )
                        out.closeEntry()
                    }
            }
        }
    }

    private
    fun eraseMethods(bytes: ByteArray, methods: List<String>, entryName: String, source: File): ByteArray {
        val erased = mutableSetOf<String>()
        val writer = ClassWriter(0)
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor? {
                val method = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name !in methods) {
                    return method
                }
                erased += name
                method.visitCode()
                method.visitInsn(Opcodes.RETURN)
                method.visitMaxs(0, Type.getArgumentTypes(descriptor).sumOf { it.size } + if (access and Opcodes.ACC_STATIC == 0) 1 else 0)
                method.visitEnd()
                // Leaving the original body unvisited is what replaces it.
                return null
            }
        }, 0)
        check(erased.containsAll(methods)) {
            "No method ${methods - erased} in $entryName of $source - check what the library does instead now"
        }
        return writer.toByteArray()
    }

    private
    fun String.toInternalName() = replace('.', '/')

    private
    fun String.toEntryName() = "${toInternalName()}.class"

    /** The JDK a multi-release copy is meant for, or 0 for the copy that any JDK reads. */
    private
    fun ZipEntry.javaVersion() = multiReleaseVersionPrefix.find(name)?.groupValues?.get(1)?.toInt() ?: 0

    /** A multi-release Jar carries a copy of a class per JDK, and all of them stand for the same class. */
    private
    fun String.withoutVersionPrefix() = multiReleaseVersionPrefix.replace(this, "")
}
