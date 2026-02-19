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

package org.gradle.dsl.tooling.builders

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.InitScriptsModel
import org.gradle.tooling.model.buildscript.ProjectScriptsModel
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifierInternal
import org.gradle.tooling.model.buildscript.ScriptComponentSources
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
import org.gradle.tooling.model.buildscript.SettingsScriptModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable

data class StandardGradleScriptModel(
    private val scriptFile: File,
    private val implicitImports: List<String>,
    private val contextPath: List<ScriptContextPathElement>,
) : GradleScriptModel, Serializable {

    override fun getScriptFile(): File = scriptFile
    override fun getImplicitImports(): List<String> = implicitImports
    override fun getContextPath(): List<ScriptContextPathElement> = contextPath
}

data class StandardScriptContextPathElement(
    private val classPath: File,
    private val sourcePath: List<ScriptComponentSourceIdentifier>
) : ScriptContextPathElement, Serializable {
    override fun getClassPathElement(): File =
        classPath

    override fun getSourcePathIdentifiers(): List<ScriptComponentSourceIdentifier> =
        sourcePath
}

data class StandardScriptComponentSourceIdentifier(
    private val displayName: String,
    val bytes: ByteArray,
) : ScriptComponentSourceIdentifierInternal, Serializable {
    override fun getDisplayName(): String =
        displayName

    override fun getComponentIdentifierBytes(): ByteArray =
        bytes

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StandardScriptComponentSourceIdentifier
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}

data class StandardInitScriptsModel(
    private val initScriptModels: List<GradleScriptModel>
) : InitScriptsModel, Serializable {
    override fun getInitScriptModels(): List<GradleScriptModel> = initScriptModels
}


data class StandardSettingsScriptModel(
    private val settingsScriptModel: GradleScriptModel
) : SettingsScriptModel, Serializable {
    override fun getSettingsScriptModel(): GradleScriptModel = settingsScriptModel
}

data class StandardProjectScriptsModel(
    private val buildScriptModel: GradleScriptModel,
    private val precompiledScriptModels: List<GradleScriptModel>
) : ProjectScriptsModel, Serializable {
    override fun getBuildScriptModel(): GradleScriptModel = buildScriptModel
    override fun getPrecompiledScriptModels(): List<GradleScriptModel> = precompiledScriptModels
}

class StandardScriptComponentSources(
    private val state: Map<ScriptComponentSourceIdentifier, List<File>>
) : ScriptComponentSources, Serializable {
    override fun getSourcesByComponents(): Map<ScriptComponentSourceIdentifier, List<File>> =
        state
}

internal fun serialize(componentId: ComponentIdentifier): ByteArray {
    val bytes = ByteArrayOutputStream()
    val encoder = KryoBackedEncoder(bytes)
    val serializer = ComponentIdentifierSerializer()
    serializer.write(encoder, componentId)
    encoder.flush()
    return bytes.toByteArray()
}

internal fun deserialize(bytes: ByteArray): ComponentIdentifier {
    val decoder = KryoBackedDecoder(bytes.inputStream())
    val serializer = ComponentIdentifierSerializer()
    return serializer.read(decoder)
}
