package org.gradle.client.ui.build

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.client.logic.build.Build
import org.gradle.client.logic.database.BuildsRepository
import org.gradle.client.logic.files.AppDirs
import org.gradle.client.ui.util.componentScope

sealed interface BuildModel {
    data object Loading : BuildModel
    data class Loaded(val build: Build) : BuildModel
}

class BuildComponent(
    context: ComponentContext,
    private val appDirs: AppDirs,
    private val buildsRepository: BuildsRepository,
    private val id: String,
    private val onFinished: () -> Unit
) : ComponentContext by context {

    private val mutableModel = MutableValue<BuildModel>(BuildModel.Loading)
    val model: Value<BuildModel> = mutableModel

    private val mutableGradleVersions = MutableValue(emptyList<String>())
    val gradleVersions: Value<List<String>> = mutableGradleVersions

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
        scope.launch {
            val versionsFile = appDirs.cacheDirectory.resolve("gradle-versions.json")
            val json = Json {
                isLenient = true
                ignoreUnknownKeys = true
            }
            if (versionsFile.isFile) {
                val versions: List<VersionInfo> = json.decodeFromString(versionsFile.readText())
                mutableGradleVersions.value = versions.map { it.version }
            }
            HttpClient().use { httpClient ->
                val versionsUrl = "https://services.gradle.org/versions/all"
                val versionsJson = httpClient.get(versionsUrl).bodyAsText()
                val versions: List<VersionInfo> = json.decodeFromString(versionsJson)
                mutableGradleVersions.value = versions.map { it.version }
                versionsFile.writeText(versionsJson)
            }
        }
    }

    fun onCloseClicked() {
        onFinished()
    }
}

@Serializable
data class VersionInfo(val version: String)
