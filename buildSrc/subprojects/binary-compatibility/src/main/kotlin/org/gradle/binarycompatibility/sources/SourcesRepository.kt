/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.binarycompatibility.sources

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.visitor.GenericVisitor

import org.jetbrains.kotlin.psi.KtFile
import parser.KotlinSourceParser

import org.gradle.util.TextUtil.normaliseFileSeparators

import java.io.File


internal
sealed class ApiSourceFile {

    internal
    abstract val currentFile: File

    internal
    abstract val currentSourceRoot: File

    data class Java internal constructor(

        override val currentFile: File,

        override val currentSourceRoot: File

    ) : ApiSourceFile()

    data class Kotlin internal constructor(

        override val currentFile: File,

        override val currentSourceRoot: File

    ) : ApiSourceFile()
}


internal
data class JavaSourceQuery<T : Any?>(
    val defaultValue: T,
    val visitor: GenericVisitor<T, Unit?>
)


internal
class SourcesRepository(

    private
    val sourceRoots: List<File>,

    private
    val compilationClasspath: List<File>

) : AutoCloseable {

    private
    val openJavaCompilationUnitsByFile = mutableMapOf<File, CompilationUnit>()

    private
    val openKotlinCompilationUnitsByRoot = mutableMapOf<File, KotlinSourceParser.ParsedKotlinFiles>()

    fun <T : Any?> executeQuery(apiSourceFile: ApiSourceFile.Java, query: JavaSourceQuery<T>): T =
        openJavaCompilationUnitsByFile
            .computeIfAbsent(apiSourceFile.currentFile) { JavaParser.parse(it) }
            .accept(query.visitor, null)
            ?: query.defaultValue


    fun <T : Any?> executeQuery(apiSourceFile: ApiSourceFile.Kotlin, transform: (KtFile) -> T): T =
        apiSourceFile.normalizedPath.let { sourceNormalizedPath ->
            openKotlinCompilationUnitsByRoot
                .computeIfAbsent(apiSourceFile.currentSourceRoot) {
                    KotlinSourceParser().parseSourceRoots(
                        listOf(apiSourceFile.currentSourceRoot),
                        compilationClasspath
                    )
                }
                .ktFiles
                .first { it.normalizedPath == sourceNormalizedPath }
                .let(transform)
        }

    override fun close() {
        val errors = mutableListOf<Exception>()
        openKotlinCompilationUnitsByRoot.values.forEach { unit ->
            try {
                unit.close()
            } catch (ex: Exception) {
                errors.add(ex)
            }
        }
        openJavaCompilationUnitsByFile.clear()
        openKotlinCompilationUnitsByRoot.clear()
        if (errors.isNotEmpty()) {
            throw Exception("Sources repository did not close cleanly").apply {
                errors.forEach(this::addSuppressed)
            }
        }
    }

    /**
     * @return the source file and it's source root
     */
    fun sourceFileAndSourceRootFor(sourceFilePath: String): Pair<File, File> =
        sourceRoots.asSequence()
            .map { it.resolve(sourceFilePath) to it }
            .firstOrNull { it.first.isFile }
            ?: throw IllegalStateException("Source file '$sourceFilePath' not found, searched in source roots:\n${sourceRoots.joinToString("\n  - ")}")

    private
    val KtFile.normalizedPath: String?
        get() = virtualFile.canonicalPath?.let { normaliseFileSeparators(it) }

    private
    val ApiSourceFile.normalizedPath: String
        get() = normaliseFileSeparators(currentFile.canonicalPath)
}
