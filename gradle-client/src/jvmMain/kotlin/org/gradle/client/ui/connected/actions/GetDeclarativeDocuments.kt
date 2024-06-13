package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.times
import org.gradle.client.build.action.GetResolvedDomAction
import org.gradle.client.build.model.ResolvedDomPrerequisites
import org.gradle.client.core.gradle.dcl.*
import org.gradle.client.ui.build.BuildTextField
import org.gradle.client.ui.composables.*
import org.gradle.client.ui.connected.TwoPanes
import org.gradle.client.ui.theme.spacing
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.*
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayOriginContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils
import org.gradle.internal.declarativedsl.evaluator.runner.stepResultOrPartialResult
import org.gradle.tooling.BuildAction
import org.jetbrains.skiko.Cursor
import java.io.File

private const val NOTHING_DECLARED = "Nothing declared"

class GetDeclarativeDocuments : GetModelAction.GetCompositeModelAction<ResolvedDomPrerequisites> {

    override val displayName: String
        get() = "Declarative Documents"

    override val modelType = ResolvedDomPrerequisites::class

    override val buildAction: BuildAction<ResolvedDomPrerequisites> = GetResolvedDomAction()

    @Composable
    @Suppress("LongMethod")
    override fun ColumnScope.ModelContent(model: ResolvedDomPrerequisites) {
        val selectedBuildFile = remember { mutableStateOf<File>(model.declarativeBuildFiles.first()) }
        val fileUpdatesCount = remember { mutableStateOf<Long>(0) }

        val buildFileContent =
            remember(selectedBuildFile.value, fileUpdatesCount.value) { selectedBuildFile.value.readText() }
        val settingsFileContent = remember(model.settingsFile) { model.settingsFile.readText() }

        val analyzer = analyzer(model)
        val projectResult = remember(selectedBuildFile.value, buildFileContent) {
            analyzer.evaluate(selectedBuildFile.value.name, buildFileContent)
        }
        val settingsResult = remember(model.settingsFile, settingsFileContent) {
            analyzer.evaluate(model.settingsFile.name, settingsFileContent)
        }
        val domWithConventions = AnalysisDocumentUtils.documentWithConventions(settingsResult, projectResult)

        val highlightedSourceRangeByFileId = mutableStateOf(mapOf<String, IntRange>())

        val hasAnyConventionContent =
            domWithConventions?.overlayNodeOriginContainer?.collectToMap(domWithConventions.document)
                ?.any { it.value !is FromOverlay } == true

        TitleLarge(displayName)
        DeclarativeFileDropDown(
            model.rootDir,
            model.declarativeBuildFiles,
            selectedBuildFile
        )
        MaterialTheme.spacing.VerticalLevel4()
        TwoPanes(
            leftWeight = 0.4f, rightWeight = 0.6f,
            verticallyScrollable = false,
            horizontallyScrollable = true,
            left = {
                if (domWithConventions != null) {
                    val projectEvaluationSchema = projectResult.stepResults.values.single()
                        .stepResultOrPartialResult.evaluationSchema

                    val projectAnalysisSchema = projectEvaluationSchema.analysisSchema

                    val mutationApplicability =
                        MutationUtils.checkApplicabilityForOverlay(projectAnalysisSchema, domWithConventions)

                    val highlightingContext = HighlightingContext(
                        domWithConventions.overlayNodeOriginContainer,
                        highlightedSourceRangeByFileId
                    )

                    val softwareTypeNode = domWithConventions.document.singleSoftwareTypeNode
                    val softwareTypeSchema = projectAnalysisSchema.softwareTypeNamed(softwareTypeNode.name)
                    val softwareTypeType =
                        projectAnalysisSchema.configuredTypeOf(softwareTypeSchema.softwareTypeSemantics)

                    TitleMedium(
                        text = "Software Type: ${softwareTypeNode.name}",
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                            .withClickTextRangeSelection(softwareTypeNode, highlightingContext)
                    )
                    MaterialTheme.spacing.VerticalLevel4()

                    Column {
                        with(
                            ModelTreeRendering(
                                domWithConventions.overlayResolutionContainer,
                                highlightingContext,
                                mutationApplicability,
                                onRunMutation = { mutationDefinition, mutationArgumentsContainer ->
                                    MutationUtils.runMutation(
                                        selectedBuildFile.value,
                                        domWithConventions.inputOverlay,
                                        projectEvaluationSchema,
                                        mutationDefinition,
                                        mutationArgumentsContainer
                                    )
                                    // Trigger recomposition:
                                    fileUpdatesCount.value += 1
                                }
                            )
                        ) {
                            ElementInfoOrNothingDeclared(softwareTypeType, softwareTypeNode, 0)
                        }
                    }
                }
            },
            right = {
                val sources = listOfNotNull(
                    SourceFileViewInput(
                        projectResult.sourceIdentifier().fileIdentifier,
                        buildFileContent,
                        relevantIndicesRange = null // we are interested in the whole project file content
                    ),
                    SourceFileViewInput(
                        settingsResult.sourceIdentifier().fileIdentifier,
                        settingsFileContent,
                        // Trim the settings file to just the part that contributed the relevant conventions:
                        relevantIndicesRange = domWithConventions?.inputUnderlay?.document?.relevantRange()
                    ).takeIf { hasAnyConventionContent },
                )

                SourcesColumn(
                    sources,
                    highlightedSourceRangeByFileId
                )
            },
        )
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun DeclarativeFileDropDown(
        rootDir: File,
        declarativeBuildFiles: List<File>,
        state: MutableState<File>
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            BuildTextField(
                modifier = Modifier.menuAnchor(),
                value = state.value.relativeTo(rootDir).path,
                onValueChange = { state.value = rootDir.resolve(it) },
                readOnly = true,
                label = { Text("Project definitions") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                declarativeBuildFiles.forEach { file ->
                    DropdownMenuItem(
                        text = { Text(file.relativeTo(rootDir).path) },
                        onClick = {
                            state.value = file
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

private
class ModelTreeRendering(
    val resolutionContainer: DocumentResolutionContainer,
    val highlightingContext: HighlightingContext,
    val mutationApplicability: NodeData<List<ApplicableMutation>>,
    val onRunMutation: (MutationDefinition, MutationArgumentContainer) -> Unit
) {
    private val indentDp = MaterialTheme.spacing.level2

    @Composable
    fun ModelTreeRendering.ElementInfoOrNothingDeclared(
        type: DataClass?,
        node: DeclarativeDocument.DocumentNode.ElementNode?,
        indentLevel: Int,
    ) {
        Column(Modifier.padding(start = indentLevel * indentDp)) {
            if (node == null || type == null) {
                LabelMedium(NOTHING_DECLARED)
            } else {
                type.properties.forEach { property ->
                    PropertyInfo(node.property(property.name), property)
                }
                val accessAndConfigure = type.memberFunctions.accessAndConfigure
                val accessAndConfigureNames = accessAndConfigure.map { it.simpleName }
                accessAndConfigure.forEach { subFunction ->
                    ConfiguringFunctionInfo(subFunction, node, indentLevel)
                    MaterialTheme.spacing.VerticalLevel2()
                }
                val addAndConfigure = type.memberFunctions.addAndConfigure.filter { function ->
                    function.simpleName !in accessAndConfigureNames
                }
                val addAndConfigureByName = addAndConfigure.associateBy { it.simpleName }
                val elementsByAddAndConfigure = node.content
                    .filterIsInstance<DeclarativeDocument.DocumentNode.ElementNode>()
                    .filter { it.name in addAndConfigureByName }

                elementsByAddAndConfigure.forEach { element ->
                    AddingFunctionInfo(element, indentLevel)
                }
            }
        }
    }

    @Composable
    private fun ModelTreeRendering.AddingFunctionInfo(
        element: DeclarativeDocument.DocumentNode.ElementNode,
        indentLevel: Int
    ) {
        val arguments = when (val valueNode = element.elementValues.singleOrNull()) {
            null -> ""
            is DeclarativeDocument.ValueNode.LiteralValueNode -> valueNode.value
            is DeclarativeDocument.ValueNode.ValueFactoryNode -> {
                val args = valueNode.values.single() as DeclarativeDocument.ValueNode.LiteralValueNode
                "${valueNode.factoryName}(${args.value})"
            }
        }
        val elementType =
            (resolutionContainer.data(element) as? ContainerElementResolved)?.elementType as? DataClass

        val elementTextRepresentation = "${element.name}($arguments)"

        val isEmpty = elementType == null || element.content.isEmpty()
        WithApplicableMutations(element) {
            if (isEmpty) {
                LabelMedium(
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.level2)
                        .withHoverCursor()
                        .withClickTextRangeSelection(element, highlightingContext),
                    text = elementTextRepresentation
                )
            } else {
                TitleSmall(
                    text = elementTextRepresentation,
                    modifier = Modifier
                        .withHoverCursor()
                        .withClickTextRangeSelection(element, highlightingContext)
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

    @Composable
    private fun ModelTreeRendering.ConfiguringFunctionInfo(
        subFunction: SchemaMemberFunction,
        parentNode: DeclarativeDocument.DocumentNode.ElementNode,
        indentLevel: Int
    ) {
        val functionNode = parentNode.childElementNode(subFunction.simpleName)
        val functionType = functionNode?.type(resolutionContainer) as? DataClass
        WithApplicableMutations(functionNode) {
            TitleSmall(
                text = subFunction.simpleName,
                modifier = Modifier
                    .withHoverCursor()
                    .withClickTextRangeSelection(functionNode, highlightingContext)
            )
        }
        ElementInfoOrNothingDeclared(
            functionType,
            functionNode,
            indentLevel + 1
        )
    }


    @Composable
    private fun PropertyInfo(
        propertyNode: DeclarativeDocument.DocumentNode.PropertyNode?,
        property: DataProperty
    ) {
        WithApplicableMutations(propertyNode) {
            LabelMedium(
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.level2)
                    .withHoverCursor()
                    .withClickTextRangeSelection(propertyNode, highlightingContext),
                text = "${property.name}: ${property.kotlinType.simpleName} = ${
                    propertyNode?.value?.sourceData?.text() ?: NOTHING_DECLARED
                }"
            )
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
                ApplicableMutations(element)
            }
        }
    }

    @Composable
    private fun ApplicableMutations(node: DeclarativeDocument.DocumentNode) {
        val applicableMutations = mutationApplicability.data(node)
        if (applicableMutations.isNotEmpty()) {
            var isMutationMenuVisible by remember { mutableStateOf(false) }
            val tooltip = "Applicable mutations"
            PlainTextTooltip(tooltip) {
                IconButton(
                    modifier = Modifier
                        .padding(MaterialTheme.spacing.level0)
                        .sizeIn(maxWidth = MaterialTheme.spacing.level6, maxHeight = MaterialTheme.spacing.level6),
                    onClick = { isMutationMenuVisible = true }
                ) {
                    Icon(
                        Icons.Default.Edit,
                        modifier = Modifier.size(MaterialTheme.spacing.level6),
                        contentDescription = tooltip
                    )
                }
            }
            DropdownMenu(
                expanded = isMutationMenuVisible,
                onDismissRequest = { isMutationMenuVisible = false },
            ) {
                var selectedMutation by remember { mutableStateOf<ApplicableMutation?>(null) }
                when (val mutation = selectedMutation) {
                    null -> {
                        MutationDropDownTitle(tooltip)
                        applicableMutations.forEach { applicableMutation ->
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        applicableMutation.mutationDefinition.name
                                    )
                                },
                                headlineContent = { Text(applicableMutation.mutationDefinition.name) },
                                supportingContent = { Text(applicableMutation.mutationDefinition.description) },
                                modifier = Modifier.selectable(selected = false, onClick = {
                                    selectedMutation = applicableMutation
                                }),
                            )
                        }
                    }

                    else -> {
                        var mutationArguments: List<MutationArgumentState> by remember {
                            mutableStateOf(mutation.mutationDefinition.parameters.map { parameter ->
                                when (parameter.kind) {
                                    MutationParameterKind.BooleanParameter ->
                                        MutationArgumentState.BooleanArgument(parameter)

                                    MutationParameterKind.IntParameter ->
                                        MutationArgumentState.IntArgument(parameter)

                                    MutationParameterKind.StringParameter ->
                                        MutationArgumentState.StringArgument(parameter)
                                }
                            })
                        }
                        val validArguments by derivedStateOf {
                            mutationArguments.all { argument ->
                                when (argument) {
                                    is MutationArgumentState.BooleanArgument -> argument.value != null
                                    is MutationArgumentState.IntArgument -> argument.value != null
                                    is MutationArgumentState.StringArgument -> argument.value?.isNotBlank() == true
                                }
                            }
                        }
                        MutationDropDownTitle(
                            headline = mutation.mutationDefinition.name,
                            supporting = mutation.mutationDefinition.description
                        )
                        mutationArguments.forEachIndexed { index, argument ->
                            when (argument) {
                                is MutationArgumentState.BooleanArgument ->
                                    ListItem(
                                        headlineContent = { Text(argument.parameter.name) },
                                        supportingContent = { Text(argument.parameter.description) },
                                        trailingContent = {
                                            Checkbox(
                                                checked = argument.value ?: false,
                                                onCheckedChange = { newChecked ->
                                                    mutationArguments = mutationArguments.toMutableList().apply {
                                                        this[index] = argument.copy(value = newChecked)
                                                    }
                                                }
                                            )
                                        }
                                    )

                                is MutationArgumentState.IntArgument ->
                                    ListItem(headlineContent = {
                                        OutlinedTextField(
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(argument.parameter.name) },
                                            placeholder = { Text(argument.parameter.description) },
                                            value = argument.value?.toString() ?: "",
                                            onValueChange = { newValue ->
                                                mutationArguments = mutationArguments.toMutableList().apply {
                                                    this[index] = argument.copy(value = newValue.toIntOrNull())
                                                }
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        )
                                    })

                                is MutationArgumentState.StringArgument ->
                                    ListItem(headlineContent = {
                                        OutlinedTextField(
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text(argument.parameter.name) },
                                            placeholder = { Text(argument.parameter.description) },
                                            value = argument.value ?: "",
                                            onValueChange = { newValue ->
                                                mutationArguments = mutationArguments.toMutableList().apply {
                                                    this[index] = argument.copy(value = newValue)
                                                }
                                            }
                                        )
                                    })
                            }
                        }
                        ListItem(headlineContent = {}, trailingContent = {
                            Button(
                                content = {
                                    val text = "Apply mutation"
                                    Icon(Icons.Default.Edit, text)
                                    MaterialTheme.spacing.HorizontalLevel2()
                                    Text(text)
                                },
                                enabled = validArguments,
                                onClick = {
                                    onRunMutation(
                                        mutation.mutationDefinition,
                                        mutationArguments.toMutationArgumentsContainer()
                                    )
                                    isMutationMenuVisible = false
                                },
                            )
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun MutationDropDownTitle(
    headline: String,
    supporting: String? = null
) {
    ListItem(
        headlineContent = { TitleMedium(headline) },
        supportingContent = supporting?.let { { Text(supporting) } },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
            supportingColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    )

}

private sealed interface MutationArgumentState {

    val parameter: MutationParameter<*>

    data class IntArgument(
        override val parameter: MutationParameter<*>,
        val value: Int? = null,
    ) : MutationArgumentState

    data class StringArgument(
        override val parameter: MutationParameter<*>,
        val value: String? = null
    ) : MutationArgumentState

    data class BooleanArgument(
        override val parameter: MutationParameter<*>,
        val value: Boolean? = null
    ) : MutationArgumentState
}

@Suppress("UNCHECKED_CAST")
private fun List<MutationArgumentState>.toMutationArgumentsContainer(): MutationArgumentContainer =
    mutationArguments {
        forEach { argumentState ->
            when (argumentState) {
                is MutationArgumentState.IntArgument ->
                    argument(argumentState.parameter as MutationParameter<Int>, requireNotNull(argumentState.value))

                is MutationArgumentState.StringArgument ->
                    argument(argumentState.parameter as MutationParameter<String>, requireNotNull(argumentState.value))

                is MutationArgumentState.BooleanArgument ->
                    argument(argumentState.parameter as MutationParameter<Boolean>, requireNotNull(argumentState.value))
            }
        }
    }


private data class HighlightingContext(
    val overlayOriginContainer: OverlayOriginContainer,
    val highlightedSourceRange: MutableState<Map<String, IntRange>>
)

private fun Modifier.withHoverCursor() =
    pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.withClickTextRangeSelection(
    node: DeclarativeDocument.DocumentNode?,
    highlightingContext: HighlightingContext
) = onClick {
    fun DeclarativeDocument.DocumentNode.sourceIdentifierToRange() =
        sourceData.sourceIdentifier.fileIdentifier to sourceData.indexRange

    highlightingContext.highlightedSourceRange.value = if (node == null) {
        emptyMap()
    } else {
        val ownIdToRange = node.sourceIdentifierToRange()
        when (val origin = highlightingContext.overlayOriginContainer.data(node)) {
            is FromOverlay,
            is FromUnderlay -> mapOf(ownIdToRange)

            is MergedElements -> mapOf(ownIdToRange, origin.underlayElement.sourceIdentifierToRange())
            is ShadowedProperty -> mapOf(ownIdToRange, origin.underlayProperty.sourceIdentifierToRange())
        }
    }
}
