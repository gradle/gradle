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
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.kotlin.dsl.*
import org.gradle.tooling.model.buildscript.GradleScriptModel
import org.gradle.tooling.model.buildscript.GradleScriptsModel
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifier
import org.gradle.tooling.model.buildscript.ScriptComponentSourceIdentifierInternal
import org.gradle.tooling.model.buildscript.ScriptContextPathElement
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
                    scriptFile = script,
                    implicitImports = scriptProject?.gradle?.scriptImplicitImports ?: emptyList(),
                    contextPath = buildContextPathFor(script, scriptProject)
                )
            }
        )
    }

    private fun buildContextPathFor(script: File, scriptProject: Project?): List<ScriptContextPathElement> =
        if (scriptProject == null) emptyList()
        else buildList {
            // TODO incomplete and doesn't have the component identifiers
            val compilationClassPath = scriptProject.scriptCompilationClassPath.asFiles

            val resolvedClassPath: MutableSet<ResolvedArtifactResult> = hashSetOf()
            for (buildscript in sourceLookupScriptHandlersFor(scriptProject).asReversed()) {
                resolvedClassPath += classpathDependenciesOf(buildscript)
                    .filter { dep -> dep.id !in resolvedClassPath.map { it.id } }
            }

//            val classPathFromLoadersTxt = File("/Users/paul/src/gradle-related/gradle/${scriptProject.name}-classpath-classloader-files.txt")
//            classPathFromLoadersTxt.bufferedWriter(Charsets.UTF_8).use { writer ->
//                compilationClassPath.forEach { writer.appendLine(it.absolutePath) }
//            }
//            val classPathFromResolutionFilesTxt = File("/Users/paul/src/gradle-related/gradle/${scriptProject.name}-classpath-resolved-files.txt")
//            classPathFromResolutionFilesTxt.bufferedWriter(Charsets.UTF_8).use { writer ->
//                resolvedClassPath.forEach { writer.appendLine(it.file.absolutePath) }
//            }
//            val classPathFromResolutionIdsTxt = File("/Users/paul/src/gradle-related/gradle/${scriptProject.name}-classpath-resolved-ids.txt")
//            classPathFromResolutionIdsTxt.bufferedWriter(Charsets.UTF_8).use { writer ->
//                resolvedClassPath.forEach { writer.appendLine(it.id.componentIdentifier.displayName) }
//            }
//            val sourcePathFromResolutionTxt = File("/Users/paul/src/gradle-related/gradle/${scriptProject.name}-sourcepath-resolved.txt")
//            val sourcePathIdentifiers: List<ComponentIdentifier> =
//                resolvedClassPath.map { it.id.componentIdentifier }
//            sourcePathFromResolutionTxt.bufferedWriter(Charsets.UTF_8).use { writer ->
//                sourcePathIdentifiers.forEach { writer.appendLine(it.displayName) }
//            }

            compilationClassPath.forEach { file ->
                add(
                    StandardScriptContextPathElement(
                        file,
                        resolvedClassPath.firstOrNull { it.file == file }
                            ?.id?.componentIdentifier
                            ?.let { componentId ->
                                listOf(
                                    StandardScriptComponentSourceIdentifier(
                                        displayName = componentId.displayName,
                                        bytes = serialize(componentId)
                                    )
                                )
                            } ?: emptyList()
                    )
                )
            }
        }

    private
    fun classpathDependenciesOf(buildscript: ScriptHandler): ArtifactCollection =
        buildscript
            .configurations[CLASSPATH_CONFIGURATION]
            .incoming
            .artifactView { it.lenient(true) }
            .artifacts


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
