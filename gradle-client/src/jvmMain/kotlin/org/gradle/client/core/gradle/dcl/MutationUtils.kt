package org.gradle.client.core.gradle.dcl

import org.gradle.client.demo.mutations.*
import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.data.NodeDataContainer
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.*
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayOriginContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import java.io.File

object MutationUtils {
    val mutationCatalog = DefaultMutationDefinitionCatalog().apply {
        registerMutationDefinition(SetVersionCodeMutation)
        registerMutationDefinition(SetNamespaceMutation)
        registerMutationDefinition(addLibraryDependencyMutation)
        registerMutationDefinition(addApplicationDependencyMutation)
        registerMutationDefinition(addTestingDependencyMutation)
    }

    fun checkApplicabilityForOverlay(
        modelSchema: AnalysisSchema,
        overlayResult: DocumentOverlayResult,
    ): NodeData<List<ApplicableMutation>> {
        val overlayDocument = overlayResult.inputOverlay
        val applicability = mutationCatalog.applicabilityFor(modelSchema, overlayDocument)

        return OverlayRoutedNodeDataContainer(
            overlayResult.overlayNodeOriginContainer,
            emptyListNodeDataContainer(),
            applicability
        )
    }

    fun runMutation(
        file: File,
        documentWithResolution: DocumentWithResolution,
        schema: EvaluationSchema,
        mutationDefinition: MutationDefinition,
        mutationArgumentsContainer: MutationArgumentContainer
    ) {
        val target = TextMutationApplicationTarget(documentWithResolution, schema)
        val result = MutationAsTextRunner().runMutation(mutationDefinition, mutationArgumentsContainer, target)

        result.stepResults.filterIsInstance<ModelMutationStepResult.ModelMutationStepApplied>().lastOrNull()?.let {
            file.writeText(it.newDocumentText)
        }
    }
}

private fun <T> emptyListNodeDataContainer(): NodeData<List<T>> = object : NodeData<List<T>> {
    override fun data(node: DeclarativeDocument.DocumentNode.ElementNode): List<T> = emptyList()
    override fun data(node: DeclarativeDocument.DocumentNode.ErrorNode): List<T> = emptyList()
    override fun data(node: DeclarativeDocument.DocumentNode.PropertyNode): List<T> = emptyList()

}

/**
 * Copy-pasted from `gradle/gradle`
 * We should consider making it a public utility there
 */
internal class OverlayRoutedNodeDataContainer<DNode, DElement : DNode, DProperty : DNode, DError : DNode>(
    private val overlayOriginContainer: OverlayOriginContainer,
    private val underlay: NodeDataContainer<DNode, DElement, DProperty, DError>,
    private val overlay: NodeDataContainer<DNode, DElement, DProperty, DError>
) : NodeDataContainer<DNode, DElement, DProperty, DError> {
    override fun data(node: DeclarativeDocument.DocumentNode.ElementNode): DElement =
        when (val from = overlayOriginContainer.data(node)) {
            is FromUnderlay -> underlay.data(node)
            is FromOverlay -> overlay.data(node)
            is MergedElements -> overlay.data(from.overlayElement)
        }

    override fun data(node: DeclarativeDocument.DocumentNode.PropertyNode): DProperty =
        when (val from = overlayOriginContainer.data(node)) {
            is FromUnderlay -> underlay.data(node)
            is FromOverlay -> overlay.data(node)
            is ShadowedProperty -> overlay.data(from.overlayProperty)
        }

    override fun data(node: DeclarativeDocument.DocumentNode.ErrorNode): DError =
        when (overlayOriginContainer.data(node)) {
            is FromUnderlay -> underlay.data(node)
            is FromOverlay -> overlay.data(node)
        }
}
