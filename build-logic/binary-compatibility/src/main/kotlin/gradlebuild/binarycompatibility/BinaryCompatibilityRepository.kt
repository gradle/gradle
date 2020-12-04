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

package gradlebuild.binarycompatibility

import com.google.common.annotations.VisibleForTesting
import gradlebuild.binarycompatibility.sources.ApiSourceFile
import gradlebuild.binarycompatibility.sources.JavaSourceQueries
import gradlebuild.binarycompatibility.sources.KotlinSourceQueries
import gradlebuild.binarycompatibility.sources.SourcesRepository
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiMethod
import javassist.bytecode.SourceFileAttribute
import java.io.File


/**
 * Repository of sources for binary compatibility checks.
 *
 * `WARN` Holds resources open for performance, must be closed after use.
 */
class BinaryCompatibilityRepository internal constructor(

    private
    val sources: SourcesRepository

) : AutoCloseable {

    companion object {

        @JvmStatic
        fun openRepositoryFor(sourceRoots: List<File>, compilationClasspath: List<File>) =
            BinaryCompatibilityRepository(SourcesRepository(sourceRoots, compilationClasspath))
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

    private
    fun apiSourceFileFor(member: JApiCompatibility): ApiSourceFile =
        member.jApiClass.let { declaringClass ->
            sources.sourceFileAndSourceRootFor(declaringClass.sourceFilePath).let { (sourceFile, sourceRoot) ->
                if (declaringClass.isKotlin) ApiSourceFile.Kotlin(sourceFile, sourceRoot)
                else ApiSourceFile.Java(sourceFile, sourceRoot)
            }
        }

    private
    val JApiClass.sourceFilePath: String
        get() = if (isKotlin) kotlinSourceFilePath else javaSourceFilePath

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
