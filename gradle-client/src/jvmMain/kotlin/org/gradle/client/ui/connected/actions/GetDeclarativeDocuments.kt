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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.times
import org.gradle.client.build.action.GetResolvedDomAction
import org.gradle.client.build.model.ResolvedDomPrerequisites
import org.gradle.client.core.gradle.dcl.analyzer
import org.gradle.client.ui.build.BuildTextField
import org.gradle.client.ui.composables.LabelMedium
import org.gradle.client.ui.composables.TitleLarge
import org.gradle.client.ui.composables.TitleMedium
import org.gradle.client.ui.composables.TitleSmall
import org.gradle.client.ui.connected.TwoPanes
import org.gradle.client.ui.theme.spacing
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils.resolvedDocument
import org.gradle.internal.declarativedsl.evaluator.runner.stepResultOrPartialResult
import org.gradle.tooling.BuildAction
import org.jetbrains.skiko.Cursor
import java.io.File

private const val NOTHING_DECLARED = "Nothing declared"

@OptIn(ExperimentalFoundationApi::class)
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

        val buildFileRelativePath = selectedBuildFile.value.relativeTo(model.rootDir).path
        val schema = model.analysisSchema

        val analyzer = analyzer(model)
        val projectResult = analyzer.evaluate(selectedBuildFile.value.name, buildFileContent)
        val dom = projectResult.stepResults.values.single().stepResultOrPartialResult.resolvedDocument()

        val highlightedSourceRange = mutableStateOf<IntRange?>(null)

        val highlightedSource by derivedStateOf {
            buildAnnotatedString {
                when (val range = highlightedSourceRange.value) {
                    null -> append(buildFileContent)
                    else -> {
                        append(buildFileContent.substring(0, range.first))
                        withStyle(style = SpanStyle(background = Color.Yellow)) {
                            append(buildFileContent.substring(range))
                        }
                        append(buildFileContent.substring(range.last + 1))
                    }
                }
            }
        }

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
                val softwareTypeNode = dom.singleSoftwareTypeNode
                val softwareTypeSchema = schema.softwareTypeNamed(softwareTypeNode.name)
                val softwareTypeType = schema.configuredTypeOf(softwareTypeSchema.softwareTypeSemantics)

                TitleMedium(
                    text = "Software Type: ${softwareTypeNode.name}",
                    modifier = Modifier
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                        .onClick {
                            highlightedSourceRange.value = softwareTypeNode.sourceData.indexRange
                        }
                )
                MaterialTheme.spacing.VerticalLevel4()
                Column {
                    softwareTypeType.properties.forEach { property ->
                        LabelMedium(
                            modifier = Modifier
                                .padding(bottom = MaterialTheme.spacing.level2)
                                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                                .onClick {
                                    highlightedSourceRange.value =
                                        softwareTypeNode.propertySourceData(property.name)?.indexRange
                                },
                            text = "${property.name}: ${property.kotlinType.simpleName} = ${
                                softwareTypeNode.propertyValue(property.name) ?: NOTHING_DECLARED
                            }"
                        )
                    }
                    Spacer(Modifier.size(MaterialTheme.spacing.level2))
                    softwareTypeType.memberFunctions.accessAndConfigure.forEach { function ->
                        val functionType = schema.configuredTypeOf(function.accessAndConfigureSemantics)
                        val functionNode = softwareTypeNode.childElementNode(function.simpleName)
                        TitleSmall(
                            text = function.simpleName,
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                                .onClick {
                                    highlightedSourceRange.value = functionNode?.sourceData?.indexRange
                                }
                        )
                        AccessAndConfigureFunction(
                            schema,
                            highlightedSourceRange,
                            functionType,
                            functionNode,
                            indentLevel = 1,
                        )
                        MaterialTheme.spacing.VerticalLevel2()
                    }
                }
            },
            right = {
                Column {
                    TitleMedium(buildFileRelativePath)
                    MaterialTheme.spacing.VerticalLevel4()
                    SelectionContainer {
                        Text(
                            text = highlightedSource,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            },
        )
    }

    private val indentDp = MaterialTheme.spacing.level2

    @Composable
    private fun AccessAndConfigureFunction(
        schema: AnalysisSchema,
        highlightedSourceRange: MutableState<IntRange?>,
        type: DataClass,
        node: DeclarativeDocument.DocumentNode.ElementNode?,
        indentLevel: Int,
    ) {
        Column(Modifier.padding(start = indentLevel * indentDp)) {
            if (node == null) {
                LabelMedium(NOTHING_DECLARED)
            } else {
                type.properties.forEach { property ->
                    LabelMedium(
                        modifier = Modifier.padding(bottom = MaterialTheme.spacing.level2)
                            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                            .onClick {
                                highlightedSourceRange.value =
                                    node.propertySourceData(property.name)?.indexRange
                            },
                        text = "${property.name}: ${property.kotlinType.simpleName} = ${
                            node.propertyValue(property.name) ?: NOTHING_DECLARED
                        }"
                    )
                }
                val accessAndConfigure = type.memberFunctions.accessAndConfigure
                val accessAndConfigureNames = accessAndConfigure.map { it.simpleName }
                accessAndConfigure.forEach { subFunction ->
                    val functionType = schema.configuredTypeOf(subFunction.accessAndConfigureSemantics)
                    val functionNode = node.childElementNode(subFunction.simpleName)
                    TitleSmall(
                        text = subFunction.simpleName,
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                            .onClick {
                                highlightedSourceRange.value = functionNode?.sourceData?.indexRange
                            }
                    )
                    AccessAndConfigureFunction(
                        schema, highlightedSourceRange, functionType, functionNode, indentLevel + 1
                    )
                }
                val addAndConfigure = type.memberFunctions.addAndConfigure.filter { function ->
                    function.simpleName !in accessAndConfigureNames
                }
                val addAndConfigureByName = addAndConfigure.associateBy { it.simpleName }
                val elementsByAddAndConfigure = node.content
                    .filterIsInstance<DeclarativeDocument.DocumentNode.ElementNode>()
                    .mapNotNull { element -> addAndConfigureByName[element.name]?.let { element to it } }
                    .toMap()
                elementsByAddAndConfigure.forEach { (element, _) ->
                    val arguments = when (val valueNode = element.elementValues.single()) {
                        is DeclarativeDocument.ValueNode.LiteralValueNode -> valueNode.value
                        is DeclarativeDocument.ValueNode.ValueFactoryNode -> {
                            val args = valueNode.values.single() as DeclarativeDocument.ValueNode.LiteralValueNode
                            "${valueNode.factoryName}(${args.value})"
                        }
                    }
                    LabelMedium(
                        modifier = Modifier.padding(bottom = MaterialTheme.spacing.level2)
                            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                            .onClick { highlightedSourceRange.value = element.sourceData.indexRange },
                        text = "${element.name}($arguments)"
                    )
                }
            }
        }
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
