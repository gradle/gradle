/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.settings

import org.gradle.api.initialization.Settings
import org.gradle.internal.declarativedsl.checks.DocumentCheck
import org.gradle.internal.declarativedsl.checks.DocumentCheckFailure
import org.gradle.internal.declarativedsl.checks.DocumentCheckFailureLocation.FailedAtNode
import org.gradle.internal.declarativedsl.checks.DocumentCheckFailureReason
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolution
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolution.ElementResolution.SuccessfulElementResolution.PropertyConfiguringElementResolved
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.UnresolvedBase
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaComponent
import org.gradle.internal.declarativedsl.plugins.PluginsCollectingPluginsBlock
import org.gradle.plugin.management.PluginManagementSpec


internal
object SettingsBlocksCheck : DocumentCheck, EvaluationSchemaComponent {

    override fun documentChecks(): List<DocumentCheck> = listOf(this)

    private
    enum class SpecialOrderBlock {
        PLUGIN_MANAGEMENT, PLUGINS
    }

    override fun detectFailures(document: DeclarativeDocument, resolutionContainer: DocumentResolutionContainer): List<DocumentCheckFailure> {
        val outOfOrderNodes = mutableListOf<DocumentNode>()
        val duplicates = mutableListOf<DocumentNode>()

        var seenBlock: SpecialOrderBlock? = null

        document.content.forEach { node ->
            val specialBlockKind = isSpecialBlock(node, resolutionContainer)
            when {
                /** In the "plugins" step, the pluginManagement { ... } block is not resolved and is fine to appear above plugins { ... }; don't report it. */
                isUnresolvedPluginManagementNode(resolutionContainer, node) -> Unit

                specialBlockKind != null -> when (seenBlock) {
                    null -> seenBlock = specialBlockKind
                    specialBlockKind -> duplicates += node
                    else -> error("unexpected mixed kinds of special blocks in one resolved DOM")
                }

                seenBlock == null -> outOfOrderNodes += node
            }
        }
        return seenBlock?.let { seen ->
            outOfOrderNodes.map { asIllegalOrderFailure(seen, it) } +
                duplicates.map { asDuplicateFailure(seen, it) }
        } ?: emptyList()
    }

    private
    fun isSpecialBlock(documentNode: DocumentNode, resolutionContainer: DocumentResolutionContainer): SpecialOrderBlock? =
        when {
            isResolvedPluginManagementNode(resolutionContainer, documentNode) -> SpecialOrderBlock.PLUGIN_MANAGEMENT
            isResolvedPluginsNode(resolutionContainer, documentNode) -> SpecialOrderBlock.PLUGINS
            else -> null
        }

    private
    fun asIllegalOrderFailure(specialOrderBlock: SpecialOrderBlock, documentNode: DocumentNode): DocumentCheckFailure =
        DocumentCheckFailure(
            this@SettingsBlocksCheck, FailedAtNode(documentNode),
            when (specialOrderBlock) {
                SpecialOrderBlock.PLUGIN_MANAGEMENT -> DocumentCheckFailureReason.PluginManagementBlockOrderViolated
                SpecialOrderBlock.PLUGINS -> DocumentCheckFailureReason.PluginsBlockOrderViolated
            }
        )

    private
    fun asDuplicateFailure(specialOrderBlock: SpecialOrderBlock, documentNode: DocumentNode): DocumentCheckFailure =
        DocumentCheckFailure(
            this, FailedAtNode(documentNode), when (specialOrderBlock) {
                SpecialOrderBlock.PLUGIN_MANAGEMENT -> DocumentCheckFailureReason.DuplicatePluginManagementBlock
                SpecialOrderBlock.PLUGINS -> DocumentCheckFailureReason.DuplicatePluginsBlock
            }
        )


    private
    fun isResolvedPluginManagementNode(documentResolutionContainer: DocumentResolutionContainer, documentNode: DocumentNode): Boolean =
        documentNode is ElementNode &&
            documentNode.name == Settings::pluginManagement.name &&
            with(documentResolutionContainer.data(documentNode)) {
                this is PropertyConfiguringElementResolved && this.elementType.name.qualifiedName == PluginManagementSpec::class.qualifiedName
            }

    private
    fun isUnresolvedPluginManagementNode(documentResolutionContainer: DocumentResolutionContainer, documentNode: DocumentNode): Boolean =
        documentNode is ElementNode &&
            documentNode.name == Settings::pluginManagement.name &&
            with(documentResolutionContainer.data(documentNode)) {
                this is DocumentResolution.ElementResolution.ElementNotResolved && this.reasons == listOf(UnresolvedBase)
            }

    private
    fun isResolvedPluginsNode(documentResolutionContainer: DocumentResolutionContainer, resolvedDocumentNode: DocumentNode): Boolean =
        resolvedDocumentNode is ElementNode &&
            resolvedDocumentNode.name == "plugins" &&
            with(documentResolutionContainer.data(resolvedDocumentNode)) {
                this is PropertyConfiguringElementResolved && this.elementType.name.qualifiedName == PluginsCollectingPluginsBlock::class.qualifiedName
            }
}
