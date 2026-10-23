/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream


@CacheableTransform
abstract class Minify : TransformAction<Minify.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var minifySpecsByCoordinates: Map<String, MinifySpec>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val artifact: Provider<FileSystemLocation>

    private
    val jarArtifactRegex = Regex("""^(.*?)-\d+(\.\d+)*([.-][A-Za-z0-9]+)*\.jar$""")

    private
    val signatureFileRegex = Regex("""META-INF/[^/]+\.(SF|RSA|DSA|EC)""")

    private
    val specsByArtifacts: Map<String, MinifySpec> by lazy {
        parameters.minifySpecsByCoordinates.mapKeys { it.key.substringAfter(":") }
    }

    override fun transform(outputs: TransformOutputs) {
        val fileName = artifact.get().asFile.name
        val artifactName = extractArtifactName(fileName)
        val spec = specsByArtifacts[artifactName]
        if (spec == null) {
            outputs.file(artifact)
        } else {
            minify(artifact.get().asFile, spec, outputs.file("${artifact.get().asFile.nameWithoutExtension}-min.jar"))
        }
    }

    private
    fun extractArtifactName(fileName: String): String {
        return jarArtifactRegex.matchEntire(fileName)
            ?.groupValues
            ?.get(1)
            ?: error("Cannot derive artifact name from: $fileName")
    }

    private
    fun minify(artifact: File, spec: MinifySpec, output: File) {
        val removedPrefixes = spec.removePackages.map { it.replace('.', '/') + "/" }
        val stubbedMethods = spec.stubbedMethods.groupBy({ it.substringBefore('#').replace('.', '/') }, { stubbedMethodKey(it) })

        val classes = linkedMapOf<String, ByteArray>()
        val resources = linkedMapOf<String, ByteArray>()
        ZipFile(artifact).use { zip ->
            for (entry in zip.entries()) {
                val name = entry.name
                if (entry.isDirectory || isDropped(name, removedPrefixes)) {
                    continue
                }
                val bytes = zip.getInputStream(entry).readBytes()
                if (name.endsWith(".class")) {
                    classes[name.removeSuffix(".class")] = bytes
                } else {
                    resources[name] = bytes
                }
            }
        }

        // Stub before computing what is reachable, so that what only the stubbed methods refer to goes too
        classes.replaceAll { name, bytes -> rewrite(bytes, spec.dropLocalVariables, stubbedMethods[name].orEmpty()) }
        val kept = if (spec.removeUnreachable) reachable(classes, keepPredicate(spec)) else classes.keys

        ZipOutputStream(output.outputStream().buffered()).use { out ->
            out.setLevel(Deflater.BEST_COMPRESSION)
            resources.forEach { (name, bytes) -> out.putEntry(name, bytes) }
            classes.forEach { (name, bytes) -> if (name in kept) out.putEntry("$name.class", bytes) }
        }
    }

    private
    fun isDropped(name: String, removedPrefixes: List<String>) =
        signatureFileRegex.matches(name) ||
            name.endsWith("module-info.class") ||
            // TODO: this drops the classes of multi-release jars (bcprov has some for Java 11, 15, 17 and 25), as ProGuard did.
            //  Keeping the overlays of the kept classes would be more faithful, at about 1MB for bcprov.
            name.startsWith("META-INF/versions/") ||
            removedPrefixes.any { name.startsWith(it) }

    private
    fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private
    fun stubbedMethodKey(method: String): String {
        val signature = method.substringAfter('#')
        require(signature.startsWith("void ") && signature.endsWith("()")) { "Only `void method()` can be stubbed: $method" }
        return signature.removePrefix("void ").removeSuffix("()") + "()V"
    }

    private
    fun keepPredicate(spec: MinifySpec): (String) -> Boolean {
        val rules = spec.keepClasses.map { keep ->
            // An exclusion does not apply to a pattern it is a prefix of, so that a narrower pattern can keep part of an excluded package
            val exclusions = spec.excludedClasses.filterNot { keep.startsWith(it.substringBefore("*")) }
            classNameRegex(keep) to exclusions.map(::classNameRegex)
        }
        return { className -> rules.any { (keep, exclusions) -> keep.matches(className) && exclusions.none { it.matches(className) } } }
    }

    /**
     * ProGuard's class name patterns: `**` matches any part of a name, `*` any part that has no package separator.
     */
    private
    fun classNameRegex(pattern: String) =
        Regex(pattern.split("**").joinToString(".*") { part -> part.split("*").joinToString("[^.]*") { Regex.escape(it) } })

    private
    fun reachable(classes: Map<String, ByteArray>, isKept: (String) -> Boolean): Set<String> {
        val kept = classes.keys.filterTo(hashSetOf()) { isKept(it.replace('/', '.')) }
        val queue = ArrayDeque(kept)
        while (queue.isNotEmpty()) {
            for (referenced in referencedClasses(classes.getValue(queue.removeFirst()))) {
                if (referenced in classes && kept.add(referenced)) {
                    queue.addLast(referenced)
                }
            }
        }
        return kept
    }

    /**
     * The classes a class needs: its supertypes, its outer class, and the classes in its member signatures,
     * annotations and code. Generic signatures and inner class entries do not count.
     */
    private
    fun referencedClasses(bytes: ByteArray): Set<String> {
        val referenced = hashSetOf<String>()

        fun type(type: Type) {
            when (type.sort) {
                Type.ARRAY -> type(type.elementType)
                Type.OBJECT -> referenced.add(type.internalName)
                Type.METHOD -> {
                    type(type.returnType)
                    type.argumentTypes.forEach(::type)
                }
            }
        }

        fun descriptor(descriptor: String) = type(Type.getType(descriptor))
        fun name(name: String?) {
            if (name != null) {
                type(if (name.startsWith("[")) Type.getType(name) else Type.getObjectType(name))
            }
        }
        fun constant(value: Any?) {
            when (value) {
                is Type -> type(value)
                is Handle -> {
                    name(value.owner)
                    descriptor(value.desc)
                }
            }
        }

        val annotationVisitor = object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String?, value: Any?) = constant(value)
            override fun visitEnum(name: String?, descriptor: String, value: String?) = descriptor(descriptor)
            override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor = also { descriptor(descriptor) }
            override fun visitArray(name: String?): AnnotationVisitor = this
        }
        val methodVisitor = object : MethodVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = annotationVisitor.also { descriptor(descriptor) }
            override fun visitTypeInsn(opcode: Int, type: String) = name(type)
            override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                name(owner)
                descriptor(descriptor)
            }

            override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                name(owner)
                descriptor(descriptor)
            }

            override fun visitInvokeDynamicInsn(name: String, descriptor: String, bootstrapMethodHandle: Handle, vararg bootstrapMethodArguments: Any?) {
                descriptor(descriptor)
                constant(bootstrapMethodHandle)
                bootstrapMethodArguments.forEach(::constant)
            }

            override fun visitLdcInsn(value: Any?) = constant(value)
            override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) = descriptor(descriptor)
            override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) = name(type)
        }
        val fieldVisitor = object : FieldVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = annotationVisitor.also { descriptor(descriptor) }
        }

        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                name(superName)
                interfaces?.forEach(::name)
            }

            override fun visitOuterClass(owner: String, name: String?, descriptor: String?) = name(owner)
            override fun visitNestHost(nestHost: String) = name(nestHost)
            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = annotationVisitor.also { descriptor(descriptor) }
            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor =
                fieldVisitor.also { descriptor(descriptor) }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                descriptor(descriptor)
                exceptions?.forEach(::name)
                return methodVisitor
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return referenced
    }

    private
    fun rewrite(bytes: ByteArray, dropLocalVariables: Boolean, stubbedMethods: List<String>): ByteArray {
        if (!dropLocalVariables && stubbedMethods.isEmpty()) {
            return bytes
        }
        // No reader passed to the writer, so that the constant pool is rebuilt without what was dropped
        val writer = ClassWriter(0)
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9, writer) {
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                val method = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name + descriptor in stubbedMethods) {
                    method.visitCode()
                    method.visitInsn(Opcodes.RETURN)
                    val isStatic = access and Opcodes.ACC_STATIC != 0
                    method.visitMaxs(0, (Type.getArgumentsAndReturnSizes(descriptor) shr 2) - (if (isStatic) 1 else 0))
                    method.visitEnd()
                    return null
                }
                if (!dropLocalVariables) {
                    return method
                }
                return object : MethodVisitor(Opcodes.ASM9, method) {
                    override fun visitLocalVariable(name: String, descriptor: String, signature: String?, start: Label, end: Label, index: Int) = Unit
                }
            }
        }, 0)
        return writer.toByteArray()
    }
}
