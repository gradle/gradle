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

import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.internal.execution.FileCollectionSnapshotter
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotVisitResult
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import java.io.File
import kotlin.metadata.internal.metadata.deserialization.MetadataVersion

internal
class DefaultKotlinMetadataCompatibilityChecker(
    val fileCollectionSnapshotter: FileCollectionSnapshotter,
    val fileCollectionFactory: FileCollectionFactory,
    val compatibilityCache: KotlinMetadataCompatibilityCache,
    val classpathWalker: ClasspathWalker,
): KotlinMetadataCompatibilityChecker {

    override fun incompatibleClasspathElements(classPath: ClassPath): List<File> {
        val extractor = KotlinMetadataVersionExtractor()
        val elementChecker = ClasspathElementChecker(classpathWalker, extractor)

        val fileSystemSnapshot: FileSystemSnapshot = fileCollectionSnapshotter.snapshot(fileCollectionFactory.fixed(classPath.getAsFiles()))

        val incompatibleFiles = mutableListOf<File>()

        fileSystemSnapshot.accept { snapshot ->
            // if it doesn't exist, we ignore it
            if (snapshot is MissingFileSnapshot) {
                return@accept SnapshotVisitResult.CONTINUE
            }

            // if not jar file or class directory, we ignore it
            if (snapshot is RegularFileSnapshot && !snapshot.absolutePath.endsWith(".jar", ignoreCase = true)) {
                return@accept SnapshotVisitResult.CONTINUE
            }

            val file = File(snapshot.absolutePath)
            val compatible = compatibilityCache.isCompatible(snapshot.hash) {
                elementChecker.isCompatible(file)
            }
            if (!compatible) {
                incompatibleFiles.add(file)
            }

            // if it's a directory, we don't visit its content (i.e. we want to snapshot only top level directories)
            SnapshotVisitResult.SKIP_SUBTREE
        }

        return incompatibleFiles
    }

}

private
class ClasspathElementChecker(val classpathWalker: ClasspathWalker, val extractor: KotlinMetadataVersionExtractor) {

    fun isCompatible(file: File): Boolean {
        val seenPaths = hashSetOf<String>()

        var incompatibilityFound = false
        classpathWalker.visit(file) { entry ->

            if (!incompatibilityFound && entry.name.endsWith(".class")) {
                val path = entry.name.substringBeforeLast('/', "")
                if (seenPaths.add(path)) { // we check a single class from any given package (approximated by the entry name paths)
                    val classReader = ClassReader(entry.content)
                    classReader.accept(extractor.reset(), ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                    if (extractor.version != null) {
                        val metadataVersion = MetadataVersion(extractor.version!!, false)
                        val compatible = metadataVersion.isCompatibleWithCurrentCompilerVersion()
                        if (!compatible) {
                            incompatibilityFound = true
                        }
                    }
                }
            }
        }
        return !incompatibilityFound
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
