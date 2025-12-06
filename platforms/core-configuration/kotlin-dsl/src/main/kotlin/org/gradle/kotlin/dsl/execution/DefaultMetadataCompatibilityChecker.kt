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

package org.gradle.kotlin.dsl.execution

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import java.io.File
import kotlin.metadata.internal.metadata.deserialization.MetadataVersion

internal
class DefaultMetadataCompatibilityChecker(val classpathWalker: ClasspathWalker): MetadataCompatibilityChecker {

    // TODO performance
    //  cache per classpath entry
    //      key: classpath entry hash
    //      value: boolean

    override fun incompatibleClasspathElements(classPath: ClassPath): List<File> {
        val extractor = KotlinMetadataVersionExtractor()
        return classPath.asFiles.filter { file -> isIncompatible(file, extractor) }
    }

    private
    fun isIncompatible(file: File, metadataVersionExtractor: KotlinMetadataVersionExtractor): Boolean {
        var incompatibilityFound = false
        classpathWalker.visit(file) { entry ->
            if (!incompatibilityFound && entry.name.endsWith(".class")) {
                val classReader = ClassReader(entry.content)
                classReader.accept(metadataVersionExtractor.reset(), ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                if (metadataVersionExtractor.version != null) {
                    val metadataVersion = MetadataVersion(metadataVersionExtractor.version!!, false)
                    val compatible = metadataVersion.isCompatibleWithCurrentCompilerVersion()
                    if (!compatible) {
                        incompatibilityFound = true
                    }
                }
            }
        }
        return incompatibilityFound
    }
}

private
class KotlinMetadataVersionExtractor : ClassVisitor(AsmConstants.ASM_LEVEL) {
    var version: IntArray? = null

    private val annotationVisitor = MetadataAnnotationVisitor()

    fun reset(): KotlinMetadataVersionExtractor {
        version = null
        return this
    }

    // Check every annotation on the class
    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        if ("Lkotlin/Metadata;" == descriptor) {
            return annotationVisitor
        }
        return super.visitAnnotation(descriptor, visible)
    }

    private inner class MetadataAnnotationVisitor : AnnotationVisitor(AsmConstants.ASM_LEVEL) {

        override fun visit(name: String?, value: Any?) {
            if (name == "mv" || name == "metadataVersion") {
                version = value as IntArray?
            }
            super.visit(name, value)
        }
    }
}
