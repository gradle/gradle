package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import org.gradle.client.build.action.GetResolvedDomAction
import org.gradle.client.build.model.ResolvedDomPrerequisites
import org.gradle.client.core.gradle.dcl.analyzer
import org.gradle.client.core.gradle.dcl.sourceIdentifier
import org.gradle.client.core.gradle.dcl.type
import org.gradle.client.ui.build.BuildTextField
import org.gradle.client.ui.composables.LabelMedium
import org.gradle.client.ui.composables.TitleLarge
import org.gradle.client.ui.composables.TitleMedium
import org.gradle.client.ui.composables.TitleSmall
import org.gradle.client.ui.connected.TwoPanes
import org.gradle.client.ui.theme.spacing
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution.SuccessfulElementResolution.ContainerElementResolved
import org.gradle.internal.declarativedsl.dom.data.collectToMap
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayNodeOrigin.*
import org.gradle.internal.declarativedsl.dom.operations.overlay.OverlayOriginContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisSequenceResult
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

        val buildFileContent = remember(selectedBuildFile.value) { selectedBuildFile.value.readText() }
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
                    val highlightingContext = HighlightingContext(
                        domWithConventions.overlayNodeOriginContainer,
                        highlightedSourceRangeByFileId
                    )

                    val softwareTypeNode = domWithConventions.document.singleSoftwareTypeNode
                    val projectAnalysisSchema = projectResult.stepResults.values.single()
                        .stepResultOrPartialResult.evaluationSchema.analysisSchema
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
                        ElementInfoOrNothingDeclared(
                            softwareTypeType,
                            softwareTypeNode,
                            domWithConventions.overlayResolutionContainer,
                            highlightingContext,
                            0
                        )
                    }
                }
            },
            right = {
                SourcesColumn(
                    projectResult,
                    buildFileContent,
                    settingsResult,
                    settingsFileContent,
                    hasAnyConventionContent,
                    highlightedSourceRangeByFileId
                )
            },
        )
    }

    @Composable
    private fun SourcesColumn(
        projectResult: AnalysisSequenceResult,
        projectFileContent: String,
        settingsResult: AnalysisSequenceResult,
        settingsFileContent: String,
        hasAnyConventionUsage: Boolean,
        highlightedSourceRangeByFileId: MutableState<Map<String, IntRange>>
    ) {

        Column {
            val sources = listOfNotNull(
                projectResult.sourceIdentifier().fileIdentifier to projectFileContent,
                (settingsResult.sourceIdentifier().fileIdentifier to settingsFileContent).takeIf { hasAnyConventionUsage },
            ).toMap()

            val sourceFileData by derivedStateOf {
                sources.mapValues { (identifier, content) ->
                    val highlightedRangeOrNull = highlightedSourceRangeByFileId.value[identifier]
                    SourceFileData(identifier, sourceFileAnnotatedString(highlightedRangeOrNull, content))
                }
            }

            val sourceFilesData = sourceFileData.values.toList()

            sourceFilesData.forEachIndexed { index, data ->
                SourceFileTitleAndText(data.relativePath, data.annotatedSource)
                if (index != sourceFilesData.lastIndex) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    private data class SourceFileData(
        val relativePath: String,
        val annotatedSource: AnnotatedString
    )

    private fun sourceFileAnnotatedString(
        highlightedSourceRange: IntRange?,
        fileContent: String
    ) = buildAnnotatedString {
        val range = highlightedSourceRange
        when {
            range == null -> append(fileContent)

            else -> {
                append(fileContent.substring(0, range.first))
                withStyle(style = SpanStyle(background = Color.Yellow)) {
                    append(fileContent.substring(range))
                }
                append(fileContent.substring(range.last + 1))
            }
        }
    }

    @Composable
    private fun SourceFileTitleAndText(
        fileRelativePath: String,
        highlightedSource: AnnotatedString
    ) {
        TitleMedium(fileRelativePath)
        MaterialTheme.spacing.VerticalLevel4()
        SelectionContainer {
            Text(
                text = highlightedSource,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }

    private val indentDp = MaterialTheme.spacing.level2

    @Composable
    private fun ElementInfoOrNothingDeclared(
        type: DataClass?,
        node: DeclarativeDocument.DocumentNode.ElementNode?,
        resolutionContainer: DocumentResolutionContainer,
        highlightingContext: HighlightingContext,
        indentLevel: Int,
    ) {
        Column(Modifier.padding(start = indentLevel * indentDp)) {
            if (node == null || type == null) {
                LabelMedium(NOTHING_DECLARED)
            } else {
                type.properties.forEach { property ->
                    PropertyInfo(node.property(property.name), highlightingContext, property)
                }
                val accessAndConfigure = type.memberFunctions.accessAndConfigure
                val accessAndConfigureNames = accessAndConfigure.map { it.simpleName }
                accessAndConfigure.forEach { subFunction ->
                    ConfiguringFunctionInfo(
                        subFunction,
                        node,
                        resolutionContainer,
                        highlightingContext,
                        indentLevel
                    )
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
                    AddingFunctionInfo(
                        element,
                        resolutionContainer,
                        highlightingContext,
                        indentLevel
                    )
                }
            }
        }
    }

    @Composable
    private fun AddingFunctionInfo(
        element: DeclarativeDocument.DocumentNode.ElementNode,
        resolutionContainer: DocumentResolutionContainer,
        highlightingContext: HighlightingContext,
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

        if (elementType == null || element.content.isEmpty()) {
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
            ElementInfoOrNothingDeclared(
                elementType,
                element,
                resolutionContainer,
                highlightingContext,
                indentLevel + 1
            )
        }
    }

    @Composable
    private fun ConfiguringFunctionInfo(
        subFunction: SchemaMemberFunction,
        parentNode: DeclarativeDocument.DocumentNode.ElementNode,
        resolutionContainer: DocumentResolutionContainer,
        highlightingContext: HighlightingContext,
        indentLevel: Int
    ) {
        val functionNode = parentNode.childElementNode(subFunction.simpleName)
        val functionType = functionNode?.type(resolutionContainer) as? DataClass
        TitleSmall(
            text = subFunction.simpleName,
            modifier = Modifier
                .withHoverCursor()
                .withClickTextRangeSelection(functionNode, highlightingContext)
        )
        ElementInfoOrNothingDeclared(
            functionType,
            functionNode,
            resolutionContainer,
            highlightingContext,
            indentLevel + 1
        )
    }

    @Composable
    private fun PropertyInfo(
        propertyNode: DeclarativeDocument.DocumentNode.PropertyNode?,
        highlightingContext: HighlightingContext,
        property: DataProperty
    ) {
        LabelMedium(
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.level2)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                .withClickTextRangeSelection(propertyNode, highlightingContext),
            text = "${property.name}: ${property.kotlinType.simpleName} = ${
                // TODO: display non-literal values with a proper structure:
                propertyNode?.value?.sourceData?.text() ?: NOTHING_DECLARED
            }"
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
