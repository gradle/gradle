/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.nomoduleannotation

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

object Attributes {
    val artifactType = Attribute.of("artifactType", String::class.java)
    val noModuleAnnotation = Attribute.of("noModuleAnnotation", Boolean::class.javaObjectType)
}

@CacheableTransform
abstract class NoModuleAnnotation : TransformAction<NoModuleAnnotation.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var artifactNames: Set<String>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val artifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val artifactFile = artifact.get().asFile
        val fileName = artifactFile.name
        for (artifactName in parameters.artifactNames) {
            if (fileName.startsWith(artifactName)) {
                transformJar(
                    inputFile = artifactFile,
                    outputFile = outputs.file("${artifactFile.nameWithoutExtension}-no-module-annotation.jar")
                )
            } else {
                outputs.file(artifact)
            }
        }
    }

    private fun transformJar(inputFile: File, outputFile: File) {
        JarFile(inputFile).use { inputJar ->
            JarOutputStream(outputFile.outputStream().buffered()).use { outputJar ->
                inputJar.entries().iterator().forEach { entry ->
                    outputJar.putNextEntry(JarEntry(entry.name))
                    inputJar.getInputStream(entry).use { entryInput ->
                        if (entry.name.endsWith(".class")) {
                            transformClass(entryInput, outputJar)
                        } else {
                            entryInput.copyTo(outputJar)
                        }
                    }
                }
            }
        }
    }

    private fun transformClass(inputStream: InputStream, outputStream: OutputStream) {
        ClassWriter(0).let { writer ->
            ClassReader(inputStream).accept(TransformClassVisitor(writer), 0)
            outputStream.write(writer.toByteArray())
        }
    }

    private class TransformClassVisitor(parent: ClassWriter) : ClassVisitor(ASM_LEVEL, parent) {

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
            if (descriptor == TARGET_ANNOTATION_DESCRIPTOR) {
                return TransformAnnotationVisitor(super.visitAnnotation(descriptor, visible))
            }
            return super.visitAnnotation(descriptor, visible)
        }
    }

    private class TransformAnnotationVisitor(parent: AnnotationVisitor) : AnnotationVisitor(ASM_LEVEL, parent) {

        override fun visitEnum(name: String?, descriptor: String?, value: String?) {
            if (value != MODULE_TARGET_ENUM_VALUE) {
                super.visitEnum(name, descriptor, value)
            }
        }

        override fun visitArray(name: String?): AnnotationVisitor {
            return TransformAnnotationVisitor(super.visitArray(name))
        }
    }

    private companion object {
        const val TARGET_ANNOTATION_DESCRIPTOR = "Ljava/lang/annotation/Target;"
        const val MODULE_TARGET_ENUM_VALUE = "MODULE"
        const val ASM_LEVEL = Opcodes.ASM9
    }
}
