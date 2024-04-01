package org.gradle.client.ui.buildlist

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import org.gradle.client.logic.build.Build
import org.gradle.client.logic.database.BuildsRepository
import org.gradle.client.ui.AppDispatchers
import java.io.File

sealed interface BuildListModel {
    data object Loading : BuildListModel
    data class Loaded(val builds: List<Build>) : BuildListModel
}

class BuildListComponent(
    context: ComponentContext,
    private val appDispatchers: AppDispatchers,
    private val buildsRepository: BuildsRepository,
    private val onBuildSelected: (String) -> Unit
) : ComponentContext by context {

    private val mutableModel = MutableValue<BuildListModel>(BuildListModel.Loading)
    val model: Value<BuildListModel> = mutableModel

    private val scope = coroutineScope(appDispatchers.main + SupervisorJob())

    init {
        val fetchAll = scope.launch {
            buildsRepository.fetchAll().cancellable().collect { builds ->
                mutableModel.value = when (val state = model.value) {
                    BuildListModel.Loading -> BuildListModel.Loaded(builds)
                    is BuildListModel.Loaded -> state.copy(builds = builds)
                }
            }
        }
        fetchAll.invokeOnCompletion {
            mutableModel.value = BuildListModel.Loading
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
