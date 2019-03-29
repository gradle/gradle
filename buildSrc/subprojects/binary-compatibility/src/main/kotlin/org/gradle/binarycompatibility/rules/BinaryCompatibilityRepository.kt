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

package org.gradle.binarycompatibility.rules

import japicmp.model.JApiCompatibility
import japicmp.model.JApiClass
import japicmp.model.JApiMethod

import javassist.bytecode.SourceFileAttribute

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.visitor.GenericVisitor

import org.jetbrains.kotlin.psi.KtFile

import parser.KotlinSourceParser

import com.google.common.annotations.VisibleForTesting

import java.io.File


/**
 * Repository of class bytes and sources for binary compatibility.
 *
 * `WARN` Holds resources open for performance, must be closed after use.
 */
class BinaryCompatibilityRepository internal constructor(

    private
    val sources: SourcesRepository

) : AutoCloseable {

    companion object {

        @JvmStatic
        fun openRepositoryFor(currentSourceRoots: List<File>, currentClasspath: List<File>) =
            BinaryCompatibilityRepository(SourcesRepository(currentSourceRoots, currentClasspath))
    }

    @VisibleForTesting
    fun emptyCaches() =
        sources.close()

    override fun close() =
        emptyCaches()

    fun isOverride(method: JApiMethod): Boolean =
        apiSourceFileFor(method).let { apiSourceFile ->
            when (apiSourceFile) {
                is ApiSourceFile.Java -> sources.executeQuery(apiSourceFile, JavaSourceQueries.isOverrideMethod(method))
                is ApiSourceFile.Kotlin -> sources.executeQuery(apiSourceFile, KotlinSourceQueries.isOverrideMethod(method))
            }
        }

    fun isSince(version: String, member: JApiCompatibility): Boolean =
        apiSourceFileFor(member).let { apiSourceFile ->
            when (apiSourceFile) {
                is ApiSourceFile.Java -> sources.executeQuery(apiSourceFile, JavaSourceQueries.isSince(version, member))
                is ApiSourceFile.Kotlin -> sources.executeQuery(apiSourceFile, KotlinSourceQueries.isSince(version, member))
            }
        }

    fun isKotlinFileFacadeClass(member: JApiCompatibility): Boolean =
        member.takeIf { it is JApiClass && it.jApiClass.isKotlin }?.let {
            KotlinMetadataQueries.queryKotlinMetadata(
                member.jApiClass.newClass.get(),
                defaultResult = false,
                query = KotlinMetadataQueries.isKotlinFileFacadeClass()
            )
        } == true


    private
    fun apiSourceFileFor(member: JApiCompatibility): ApiSourceFile =
        sources.sourceFileAndSourceRootFor(member.jApiClass.sourceFilePath).let { (sourceFile, sourceRoot) ->
            if (member.jApiClass.isKotlin) ApiSourceFile.Kotlin(sourceFile, sourceRoot)
            else ApiSourceFile.Java(sourceFile, sourceRoot)
        }

    private
    val JApiClass.sourceFilePath: String
        get() = if (isKotlin) kotlinSourceFilePath
        else javaSourceFilePath

    private
    val JApiClass.javaSourceFilePath: String
        get() = fullyQualifiedName
            .replace(".", "/")
            .replace(innerClassesPartRegex, "") + ".java"

    private
    val innerClassesPartRegex =
        "\\$.*".toRegex()

    private
    val JApiClass.kotlinSourceFilePath: String
        get() = "$packagePath/$bytecodeSourceFilename"

    private
    val JApiClass.bytecodeSourceFilename: String
        get() = newClass.orNull()?.classFile?.getAttribute("SourceFile")?.let { it as? SourceFileAttribute }?.fileName
            ?: throw java.lang.IllegalStateException("Bytecode for $fullyQualifiedName is missing the 'SourceFile' attribute")
}


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
        openKotlinCompilationUnitsByRoot
            .computeIfAbsent(apiSourceFile.currentSourceRoot) {
                KotlinSourceParser().parseSourceRoots(
                    listOf(apiSourceFile.currentSourceRoot),
                    compilationClasspath
                )
            }
            .ktFiles
            .single { it.virtualFile.canonicalPath == apiSourceFile.currentFile.canonicalPath }
            .let(transform)

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
}
