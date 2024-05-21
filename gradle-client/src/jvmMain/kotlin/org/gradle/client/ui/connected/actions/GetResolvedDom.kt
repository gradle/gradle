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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import org.gradle.client.build.action.GetResolvedDomAction
import org.gradle.client.build.model.ResolvedDomPrerequisites
import org.gradle.client.ui.build.BuildTextField
import org.gradle.client.ui.composables.LabelMedium
import org.gradle.client.ui.composables.TitleLarge
import org.gradle.client.ui.composables.TitleMedium
import org.gradle.client.ui.composables.TitleSmall
import org.gradle.client.ui.connected.TwoPanes
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.resolvedDocument
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.gradle.internal.declarativedsl.parsing.DefaultLanguageTreeBuilder
import org.gradle.internal.declarativedsl.parsing.parse
import org.gradle.tooling.BuildAction
import org.jetbrains.skiko.Cursor
import java.io.File

private const val NOTHING_DECLARED = "Nothing declared"

@OptIn(ExperimentalFoundationApi::class)
class GetResolvedDom : GetModelAction.GetCompositeModelAction<ResolvedDomPrerequisites> {

    override val modelType = ResolvedDomPrerequisites::class

    override val buildAction: BuildAction<ResolvedDomPrerequisites> = GetResolvedDomAction()

    @Composable
    @Suppress("LongMethod")
    override fun ColumnScope.ModelContent(model: ResolvedDomPrerequisites) {

        val selectedBuildFile = remember { mutableStateOf<File>(model.declarativeBuildFiles.first()) }

        DeclarativeFileDropDown(
            model.declarativeBuildFiles,
            selectedBuildFile
        )

        val buildFileContent = remember(selectedBuildFile.value) { selectedBuildFile.value.readText() }
        // TODO hardcoded for NiA for now
        val buildFileRelativePath = selectedBuildFile.value.relativeTo(
            selectedBuildFile.value.parentFile.parentFile.parentFile
        ).path
        val schema = model.analysisSchema

        val dom = remember(model, selectedBuildFile.value) {
            val parsedLightTree = parse(buildFileContent)
            val languageTreeResult = DefaultLanguageTreeBuilder().build(
                parsedLightTree = parsedLightTree,
                sourceIdentifier = SourceIdentifier(selectedBuildFile.value.name)
            )
            resolvedDocument(
                schema = schema,
                languageTreeResult = languageTreeResult,
                analysisStatementFilter = analyzeEverything,
                strictReceiverChecks = true
            )
        }

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

        TitleLarge("Declarative Project Definitions")
        TwoPanes(
            leftWeight = 0.45f, rightWeight = 0.55f,
            scrollable = false,
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
                Column {
                    softwareTypeType.properties.forEach { property ->
                        LabelMedium(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
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
                    Spacer(Modifier.height(8.dp))
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
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            right = {
                Column {
                    TitleMedium("File: $buildFileRelativePath")
                    Spacer(Modifier.height(16.dp))
                    SelectionContainer {
                        Text(
                            text = highlightedSource,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            },
        )
    }

    private val indentDp = 8.dp

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
                        modifier = Modifier.padding(bottom = 8.dp)
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
                type.memberFunctions.accessAndConfigure.forEach { subFunction ->
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
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun DeclarativeFileDropDown(
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
                value = state.value.toString(),
                onValueChange = { state.value = File(it) },
                readOnly = true,
                label = { Text("Project definition") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                declarativeBuildFiles.forEach { file ->
                    DropdownMenuItem(
                        text = { Text(file.absolutePath) },
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
