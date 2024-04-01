package org.gradle.client.ui.build

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.client.core.database.Build
import org.gradle.client.core.database.BuildsRepository
import org.gradle.client.core.files.AppDirs
import org.gradle.client.core.gradle.GradleConnectionParameters
import org.gradle.client.ui.AppDispatchers

sealed interface BuildModel {
    data object Loading : BuildModel
    data class Loaded(val build: Build) : BuildModel
}

class BuildComponent(
    context: ComponentContext,
    private val appDispatchers: AppDispatchers,
    private val appDirs: AppDirs,
    private val buildsRepository: BuildsRepository,
    private val id: String,
    private val onConnect: (GradleConnectionParameters) -> Unit,
    private val onFinished: () -> Unit
) : ComponentContext by context {

    private val mutableModel = MutableValue<BuildModel>(BuildModel.Loading)
    val model: Value<BuildModel> = mutableModel

    private val mutableGradleVersions = MutableValue(emptyList<String>())
    val gradleVersions: Value<List<String>> = mutableGradleVersions

    private val scope = coroutineScope(appDispatchers.main + SupervisorJob())

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
        scope.launch {
            withContext(appDispatchers.io) {
                val versionsFile = appDirs.cacheDirectory.resolve("gradle-versions.json")
                val json = Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                }
                if (versionsFile.isFile) {
                    val versions = json.decodeFromString<List<VersionInfo>>(versionsFile.readText()).map { it.version }
                    withContext(appDispatchers.main) {
                        mutableGradleVersions.value = versions
                    }
                }
                HttpClient().use { httpClient ->
                    val versionsUrl = "https://services.gradle.org/versions/all"
                    val versionsJson = httpClient.get(versionsUrl).bodyAsText()
                    val versions = json.decodeFromString<List<VersionInfo>>(versionsJson).map { it.version }
                    withContext(appDispatchers.main) {
                        mutableGradleVersions.value = versions
                    }
                    versionsFile.writeText(versionsJson)
                }
            }
        }
    }

    fun onCloseClicked() {
        onFinished()
    }

    fun onConnectClicked(gradleConnectionParameters: GradleConnectionParameters) {
        onConnect(gradleConnectionParameters)
    }
}

@Serializable
data class VersionInfo(val version: String)
