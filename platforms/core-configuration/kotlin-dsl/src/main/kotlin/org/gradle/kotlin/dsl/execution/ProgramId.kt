/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.hash.HashCode
import org.gradle.internal.scripts.BuildScriptCompileUnitOfWork.BuildScriptProgramId
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import java.lang.ref.WeakReference


class ProgramId(
    val templateId: String,
    val sourceHash: HashCode,
    parentClassLoader: ClassLoader,
    private val accessorsClassPathHash: HashCode? = null,
    private val classPathHash: HashCode? = null,
    val compilerOptions: KotlinCompilerOptions = KotlinCompilerOptions(),
) : BuildScriptProgramId {

    companion object {
        const val JVM_TARGET = "jvmTarget"
        const val ALL_WARNINGS_AS_ERRORS = "allWarningsAsErrors"
        const val SKIP_METADATA_VERSION_CHECK = "skipMetadataVersionCheck"
        const val TEMPLATE_ID = "templateId"
        const val SOURCE_HASH = "sourceHash"
    }

    private
    val parentClassLoader = WeakReference(parentClassLoader)

    override fun visitIdentityInputs(visitor: UnitOfWork.InputVisitor) {
        visitor.visitInputProperty(JVM_TARGET) { compilerOptions.jvmTarget.majorVersion }
        visitor.visitInputProperty(ALL_WARNINGS_AS_ERRORS) { compilerOptions.allWarningsAsErrors }
        visitor.visitInputProperty(SKIP_METADATA_VERSION_CHECK) { compilerOptions.skipMetadataVersionCheck }
        visitor.visitInputProperty(TEMPLATE_ID) { templateId }
        visitor.visitInputProperty(SOURCE_HASH) { sourceHash }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        val that = other as? ProgramId ?: return false
        val thisParentLoader = parentClassLoader.get()
        return thisParentLoader != null
            && thisParentLoader == that.parentClassLoader.get()
            && templateId == that.templateId
            && sourceHash == that.sourceHash
            && accessorsClassPathHash == that.accessorsClassPathHash
            && classPathHash == that.classPathHash
            && compilerOptions == that.compilerOptions
    }

    override fun hashCode(): Int {
        var result = templateId.hashCode()
        result = 31 * result + sourceHash.hashCode()
        parentClassLoader.get()?.let { loader ->
            result = 31 * result + loader.hashCode()
        }
        accessorsClassPathHash?.let { classPathHash ->
            result = 31 * result + classPathHash.hashCode()
        }
        classPathHash?.let { classPathHash ->
            result = 31 * result + classPathHash.hashCode()
        }
        return 31 * result + compilerOptions.hashCode()
    }
}
