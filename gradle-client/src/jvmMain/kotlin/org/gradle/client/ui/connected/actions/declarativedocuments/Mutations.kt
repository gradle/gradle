package org.gradle.client.ui.connected.actions.declarativedocuments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.gradle.client.ui.composables.PlainTextTooltip
import org.gradle.client.ui.composables.TitleMedium
import org.gradle.client.ui.theme.spacing
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.data.NodeData
import org.gradle.internal.declarativedsl.dom.mutation.*

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ApplicableMutations(
    node: DeclarativeDocument.DocumentNode,
    mutationApplicability: NodeData<List<ApplicableMutation>>,
    onRunMutation: (MutationDefinition, MutationArgumentContainer) -> Unit
) {
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
                    argument(
                        argumentState.parameter as MutationParameter<String>,
                        requireNotNull(argumentState.value)
                    )

                is MutationArgumentState.BooleanArgument ->
                    argument(
                        argumentState.parameter as MutationParameter<Boolean>,
                        requireNotNull(argumentState.value)
                    )
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
