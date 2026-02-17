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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.hash.Hashing
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.GradleScriptsModel
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
import org.gradle.tooling.model.buildscript.SourceComponentIdentifier
import org.gradle.tooling.model.buildscript.SourceComponentIdentifierInternal
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable

object GradleScriptsModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean =
        GradleScriptsModel::class.java.name.equals(modelName)

    override fun buildAll(modelName: String, rootProject: Project): GradleScriptsModel {
        require(rootProject.rootProject == rootProject)
        val scripts: List<File> = rootProject.collectKotlinDslScripts()
        return StandardGradleScriptsModel(
            scripts.associateWith { script ->
                val scriptProject = rootProject.findProjectWithBuildFile(script)
                StandardGradleScriptModel(
                    implicitImports = scriptProject?.scriptImplicitImports ?: emptyList(),
                    contextPath = buildContextPathFor(script, scriptProject)
                )
            }
        )
    }

    private fun buildContextPathFor(script: File, scriptProject: Project?): List<ScriptContextPathElement> {
        if (scriptProject == null) return emptyList()
        val compilationClassPath = scriptProject.scriptCompilationClassPath.asFiles

        val antComponentId: ComponentIdentifier = DefaultModuleComponentIdentifier.newId(
            DefaultModuleIdentifier.newId("org.apache.ant", "ant"),
            "1.10.15"
        )
        val asmComponentId: ComponentIdentifier = DefaultModuleComponentIdentifier.newId(
            DefaultModuleIdentifier.newId("org.ow2.asm", "asm"),
            "9.9"
        )
        val antBytes = serialize(antComponentId)
        val asmBytes = serialize(asmComponentId)

        return compilationClassPath.map { file ->
            StandardScriptContextPathElement(
                file,
                listOf(
                    StandardSourceComponentIdentifier(antComponentId.displayName, antBytes),
                    StandardSourceComponentIdentifier(antComponentId.displayName, antBytes),
                    StandardSourceComponentIdentifier(asmComponentId.displayName, asmBytes),
                )
            )
        }
    }
}

class StandardGradleScriptsModel(
    private val state: Map<File, GradleScriptModel>
) : GradleScriptsModel, Serializable {
    override fun getModelsByScripts(): Map<File, GradleScriptModel> =
        state

    override fun toString(): String {
        return "StandardGradleScriptsModel(state=$state)"
    }
}

class StandardGradleScriptModel(
    private val implicitImports: List<String>,
    private val contextPath: List<ScriptContextPathElement>,
) : GradleScriptModel, Serializable {
    override fun getImplicitImports(): List<String> =
        implicitImports

    override fun getContextPath(): List<ScriptContextPathElement> =
        contextPath

    override fun toString(): String {
        return "StandardGradleScriptModel(implicitImports=${implicitImports.size}, contextPath=$contextPath)"
    }
}

class StandardScriptContextPathElement(
    private val classPath: File,
    private val sourcePath: List<SourceComponentIdentifier>
) : ScriptContextPathElement, Serializable {
    override fun getClassPath(): File =
        classPath

    override fun getSourcePath(): List<SourceComponentIdentifier> =
        sourcePath

    override fun toString(): String {
        return "StandardScriptContextPathElement(classPath=${classPath.name}, sourcePath=$sourcePath)"
    }
}

class StandardSourceComponentIdentifier(
    private val displayName: String,
    val bytes: ByteArray,
) : SourceComponentIdentifierInternal, Serializable {
    override fun getDisplayName(): String =
        displayName

    override fun getComponentIdentifierBytes(): ByteArray =
        bytes

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StandardSourceComponentIdentifier
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "StandardSourceComponentIdentifier(displayName='$displayName', bytes=${Hashing.hashBytes(bytes).toCompactString()})"
    }
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
