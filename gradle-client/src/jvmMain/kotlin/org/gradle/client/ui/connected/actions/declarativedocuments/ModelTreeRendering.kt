package org.gradle.client.ui.connected.actions.declarativedocuments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.times
import org.gradle.client.core.gradle.dcl.isUnresolvedBase
import org.gradle.client.core.gradle.dcl.type
import org.gradle.client.ui.composables.LabelMedium
import org.gradle.client.ui.composables.TitleSmall
import org.gradle.client.ui.composables.semiTransparentIfNull
import org.gradle.client.ui.connected.actions.*
import org.gradle.client.ui.theme.spacing
import org.gradle.client.ui.theme.transparency
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.ValueNode.*
import org.gradle.internal.declarativedsl.dom.DocumentNodeContainer
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved
import org.gradle.internal.declarativedsl.dom.DocumentResolution.PropertyResolution.PropertyAssignmentResolved
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.mutation.ApplicableMutation
import org.gradle.internal.declarativedsl.dom.mutation.MutationArgumentContainer
import org.gradle.internal.declarativedsl.dom.mutation.MutationDefinition
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.*
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayOriginContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.jetbrains.skiko.Cursor

internal class ModelTreeRendering(
    private val resolutionContainer: DocumentResolutionContainer,
    private val highlightingContext: HighlightingContext,
    private val mutationApplicability: NodeData<List<ApplicableMutation>>,
    private val onRunMutation: (MutationDefinition, MutationArgumentContainer) -> Unit
) {
    private val indentDp = MaterialTheme.spacing.level2

    @Composable
    fun ElementInfoOrNothingDeclared(
        type: DataClass?,
        node: DocumentNodeContainer?,
        indentLevel: Int,
    ) {
        Column(Modifier.padding(start = indentLevel * indentDp)) {
            val isUnresolvedBaseNode = node is ElementNode && resolutionContainer.isUnresolvedBase(node)
            
            if (node == null || type == null || isUnresolvedBaseNode) {
                LabelMedium(modifier = Modifier.alpha(MaterialTheme.transparency.HALF), text = NOT_DECLARED)
            } else {
                MaterialTheme.spacing.VerticalLevel2()
                type.properties.forEach { property ->
                    PropertyInfo(node.property(property.name), property)
                }
                val accessAndConfigure = type.memberFunctions.accessAndConfigure
                val accessAndConfigureNames = accessAndConfigure.map { it.simpleName }
                val addAndConfigure = type.memberFunctions.addAndConfigure.filter { function ->
                    function.simpleName !in accessAndConfigureNames
                }
                val addAndConfigureByName = addAndConfigure.associateBy { it.simpleName }
                val elementsByAddAndConfigure = node.content
                    .filterIsInstance<ElementNode>()
                    .filter { it.name in addAndConfigureByName }

                accessAndConfigure.forEach { subFunction ->
                    ConfiguringFunctionInfo(subFunction, node, indentLevel)
                    MaterialTheme.spacing.VerticalLevel2()
                }
                elementsByAddAndConfigure.forEach { element ->
                    AddingFunctionInfo(element, indentLevel)
                }
            }
        }
    }

    @Composable
    private fun AddingFunctionInfo(
        element: ElementNode,
        indentLevel: Int
    ) {
        if (resolutionContainer.isUnresolvedBase(element)) return

        val arguments = elementArgumentsString(element)
        val elementType = (resolutionContainer.data(element) as? ContainerElementResolved)?.elementType as? DataClass

        val elementTextRepresentation = "${element.name}($arguments)"

        val isEmpty = elementType == null || element.content.isEmpty()
        WithDecoration(element) {
            if (isEmpty) {
                LabelMedium(
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.level2)
                        .withHoverCursor()
                        .withClickTextRangeSelection(element, highlightingContext)
                        .semiTransparentIfNull(element),
                    text = elementTextRepresentation
                )
            } else {
                TitleSmall(
                    text = elementTextRepresentation,
                    modifier = Modifier
                        .withHoverCursor()
                        .withClickTextRangeSelection(element, highlightingContext)
                        .semiTransparentIfNull(element)
                )
            }
        }
        if (!isEmpty) {
            ElementInfoOrNothingDeclared(
                elementType,
                element,
                indentLevel + 1
            )
        }
    }

    private fun elementArgumentsString(element: ElementNode) =
        element.elementValues.joinToString { valueNode ->
            when (valueNode) {
                is LiteralValueNode -> valueNode.value.toString()
                is NamedReferenceNode -> valueNode.referenceName
                is ValueFactoryNode -> {
                    val args = valueNode.values.map {
                        (it as? LiteralValueNode)?.value ?: "..."
                    }
                    val argsString = args.joinToString(",", "(", ")")
                    "${valueNode.factoryName}$argsString"
                }
            }
        }

    @Composable
    private fun ConfiguringFunctionInfo(
        subFunction: SchemaMemberFunction,
        parentNode: DocumentNodeContainer,
        indentLevel: Int
    ) {
        if (parentNode is ElementNode && resolutionContainer.isUnresolvedBase(parentNode))
            return

        val functionNodes = parentNode.childElementNodes(resolutionContainer, subFunction)
        if (functionNodes.isNotEmpty()) {
            functionNodes.forEach { functionNode ->
                val functionType = functionNode.type(resolutionContainer) as? DataClass

                WithDecoration(functionNode) {
                    val argsString =
                        if (functionNode.elementValues.isNotEmpty()) "(${elementArgumentsString(functionNode)})" else ""
                    val title = subFunction.simpleName + argsString
                    TitleSmall(
                        text = title,
                        modifier = Modifier
                            .withHoverCursor()
                            .semiTransparentIfNull(functionType)
                            .withClickTextRangeSelection(functionNode, highlightingContext)
                    )
                }
                ElementInfoOrNothingDeclared(functionType, functionNode, indentLevel + 1)
            }
        } else {
            TitleSmall(
                text = subFunction.simpleName,
                modifier = Modifier
                    .withHoverCursor()
                    .semiTransparentIfNull(null)
                    .withClickTextRangeSelection(null, highlightingContext)
            )
            ElementInfoOrNothingDeclared(null, null, indentLevel + 1)
        }
    }


    @Composable
    private fun PropertyInfo(
        propertyNode: DeclarativeDocument.DocumentNode.PropertyNode?,
        property: DataProperty
    ) {
        if (propertyNode != null && resolutionContainer.isUnresolvedBase(propertyNode)) {
            return
        }

        WithDecoration(propertyNode) {
            val maybeInvalidDecoration =
                if (propertyNode != null && resolutionContainer.data(propertyNode) !is PropertyAssignmentResolved)
                    TextDecoration.LineThrough else TextDecoration.None
            LabelMedium(
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.level2)
                    .withHoverCursor()
                    .withClickTextRangeSelection(propertyNode, highlightingContext)
                    .semiTransparentIfNull(propertyNode),
                textStyle = TextStyle(textDecoration = maybeInvalidDecoration),
                text = "${property.name}: ${property.typeName} = ${
                    propertyNode?.value?.sourceData?.text() ?: NOTHING_DECLARED
                }"
            )
        }
    }

    @Composable
    fun WithDecoration(
        node: DeclarativeDocument.DocumentNode?,
        content: @Composable () -> Unit
    ) {
        WithApplicableMutations(node) {
            WithModelHighlighting(node) {
                content()
            }
        }
    }

    @Composable
    private fun WithModelHighlighting(
        propertyNode: DeclarativeDocument.DocumentNode?,
        content: @Composable () -> Unit,
    ) {
        val sourceData = propertyNode?.sourceData
        val isHighlighted = sourceData?.indexRange?.let { propRange ->
            highlightingContext.rangeByFileId[sourceData.sourceIdentifier.fileIdentifier]
                ?.let { highlightedRange ->
                    propRange.first >= highlightedRange.first && propRange.last <= highlightedRange.last
                }
        }
        if (isHighlighted == true) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = Color.Yellow,
            ) {
                content()
            }
        } else {
            content()
        }
    }

    @Composable
    private fun WithApplicableMutations(
        element: DeclarativeDocument.DocumentNode?,
        content: @Composable () -> Unit
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.level1),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()

            if (element != null) {
                ApplicableMutations(element, mutationApplicability, onRunMutation)
            }
        }
    }
}

internal fun Modifier.withHoverCursor() =
    pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.withClickTextRangeSelection(
    node: DeclarativeDocument.DocumentNode?,
    highlightingContext: HighlightingContext
) = onClick {
    fun DeclarativeDocument.DocumentNode.sourceIdentifierToRange() =
        sourceData.sourceIdentifier.fileIdentifier to sourceData.indexRange

    if (node == null) {
        highlightingContext.clearHighlighting()
    } else {
        val ownIdToRange = node.sourceIdentifierToRange()
        when (val origin = highlightingContext.overlayOriginContainer.data(node)) {
            is FromOverlay,
            is FromUnderlay -> highlightingContext.setHighlightingRanges(ownIdToRange)

            is MergedElements -> highlightingContext.setHighlightingRanges(
                ownIdToRange, origin.underlayElement.sourceIdentifierToRange()
            )

            is ShadowedProperty -> highlightingContext.setHighlightingRanges(
                ownIdToRange, origin.underlayProperty.sourceIdentifierToRange()
            )
        }
    }
}

internal data class HighlightingContext(
    private val viewModel: GetDeclarativeDocumentsModel,
    val overlayOriginContainer: OverlayOriginContainer,
) {
    val rangeByFileId: Map<String, IntRange>
        get() = viewModel.highlightedSourceRangeByFileId.value

    fun clearHighlighting() = viewModel.clearHighlighting()
    fun setHighlightingRanges(vararg ranges: Pair<String, IntRange>) = viewModel.setHighlightingRanges(*ranges)
}

private const val NOT_DECLARED = "Not declared"
private const val NOTHING_DECLARED = "Nothing declared"

