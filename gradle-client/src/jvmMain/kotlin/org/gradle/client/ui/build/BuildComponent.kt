package org.gradle.client.ui.build

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import org.gradle.client.logic.build.Build
import org.gradle.client.logic.database.BuildsRepository
import org.gradle.client.ui.util.componentScope

sealed interface BuildModel {
    data object Loading : BuildModel
    data class Loaded(val build: Build) : BuildModel
}

class BuildComponent(
    context: ComponentContext,
    private val buildsRepository: BuildsRepository,
    private val id: String,
    private val onFinished: () -> Unit
) : ComponentContext by context {

    private val mutableModel = MutableValue<BuildModel>(BuildModel.Loading)
    val model: Value<BuildModel> = mutableModel

    private val scope = componentScope()

    init {
        val fetch = scope.launch {
            buildsRepository.fetch(id).cancellable().collect { build ->
                mutableModel.value = when (val state = model.value) {
                    BuildModel.Loading -> BuildModel.Loaded(build)
                    is BuildModel.Loaded -> state.copy(build = build)
                }
            }
        }
        fetch.invokeOnCompletion {
            mutableModel.value = BuildModel.Loading
        }
    }

    fun onCloseClicked() {
        onFinished()
    }
}
