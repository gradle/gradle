package org.gradle.client.ui.welcome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import org.gradle.client.logic.build.Build
import org.gradle.client.logic.database.BuildsRepository
import org.gradle.client.ui.util.componentScope
import java.io.File

sealed interface WelcomeModel {
    data object Loading : WelcomeModel
    data class Loaded(val builds: List<Build>) : WelcomeModel
}

class WelcomeComponent(
    context: ComponentContext,
    private val buildsRepository: BuildsRepository,
    private val onBuildSelected: (String) -> Unit
) : ComponentContext by context {

    private val mutableModel = MutableValue<WelcomeModel>(WelcomeModel.Loading)
    val model: Value<WelcomeModel> = mutableModel

    private val scope = componentScope()

    init {
        val fetchAll = scope.launch {
            buildsRepository.fetchAll().cancellable().collect { builds ->
                mutableModel.value = when (val state = model.value) {
                    WelcomeModel.Loading -> WelcomeModel.Loaded(builds)
                    is WelcomeModel.Loaded -> state.copy(builds = builds)
                }
            }
        }
        fetchAll.invokeOnCompletion {
            mutableModel.value = WelcomeModel.Loading
        }
    }

    fun onNewBuildRootDirChosen(rootDir: File) {
        scope.launch {
            buildsRepository.insert(Build(rootDir = rootDir))
        }
    }

    fun onBuildClicked(build: Build) {
        onBuildSelected(build.id)
    }

    fun onDeleteBuildClicked(build: Build) {
        scope.launch {
            buildsRepository.delete(build)
        }
    }
}
