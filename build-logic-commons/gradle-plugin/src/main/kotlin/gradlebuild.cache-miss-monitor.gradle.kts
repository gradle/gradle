/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("UNCHECKED_CAST")

import gradlebuild.AbstractBuildScanInfoCollectingService
import gradlebuild.registerBuildScanInfoCollectingService
import org.gradle.internal.os.OperatingSystem
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskSuccessResult
import java.io.Serializable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Register a build service that monitors a list of tasks and reports CACHE_MISS if they're actually executed.
 */
if (buildCacheEnabled()) {
    registerBuildScanInfoCollectingService(CacheMissMonitorBuildService::class.java, ::isCacheMissMonitoredTask) { cacheMissInBuildLogic, cacheMissInMainBuild ->
        buildFinished {
            if ((cacheMissInBuildLogic as AtomicBoolean).get() || (cacheMissInMainBuild as AtomicBoolean).get()) {
                tag("CACHE_MISS")
            }
        }
    }
}

fun buildCacheEnabled() = gradle.startParameter.isBuildCacheEnabled

abstract class CacheMissMonitorBuildService : AbstractBuildScanInfoCollectingService() {
    private val cacheMiss: AtomicBoolean = AtomicBoolean(false)
    override val collectedInformation: Serializable = cacheMiss

    override fun action(taskPath: String, taskResult: TaskOperationResult) {
        if (taskResult is TaskSuccessResult && !taskResult.isFromCache && !taskResult.isUpToDate) {
            println("CACHE_MISS in task $taskPath")
            cacheMiss.set(true)
        }
    }
}


/**
 *  We monitor some tasks in non-seed builds. If a task executed in a non-seed build, we think it as "CACHE_MISS".
 */
fun isCacheMissMonitoredTask(task: Task) = task.isCompileCacheMissMonitoredTask() || task.project.isAsciidoctorCacheMissTask()

fun Task.isCompileCacheMissMonitoredTask() = isMonitoredCompileTask() && !project.isExpectedCompileCacheMiss()

fun Project.isAsciidoctorCacheMissTask() = isMonitoredAsciidoctorTask() && !isExpectedAsciidoctorCacheMiss()

fun Task.isMonitoredCompileTask() = (this is AbstractCompile || this.isClasspathManifest()) && !isKotlinJsIrLink() 

fun Task.isClasspathManifest() = this.javaClass.simpleName.startsWith("ClasspathManifest")

// https://youtrack.jetbrains.com/issue/KT-49915
fun Task.isKotlinJsIrLink() = this.javaClass.simpleName.startsWith("KotlinJsIrLink")

fun isMonitoredAsciidoctorTask() = false // No asciidoctor tasks are cacheable for now

fun Project.isExpectedAsciidoctorCacheMiss() =
// Expected cache-miss for asciidoctor task:
// 1. CompileAll is the seed build for docs:distDocs
// 2. BuildDistributions is the seed build for other asciidoctor tasks
// 3. buildScanPerformance test, which doesn't depend on compileAll
// 4. buildScanPerformance test, which doesn't depend on compileAll
    isInBuild(
        "Check_CompileAllBuild",
        "Check_BuildDistributions",
        "Component_GradlePlugin_Performance_PerformanceLatestMaster",
        "Component_GradlePlugin_Performance_PerformanceLatestReleased"
    )

fun Project.isExpectedCompileCacheMiss() =
// Expected cache-miss:
// 1. CompileAll is the seed build
// 2. Gradleception which re-builds Gradle with a new Gradle version
// 3. buildScanPerformance test, which doesn't depend on compileAll
// 4. buildScanPerformance test, which doesn't depend on compileAll
// 5. BuildCommitDistribution may build a commit which is not built before
    isInBuild(
        "Check_CompileAllBuild",
        "Component_GradlePlugin_Performance_PerformanceLatestMaster",
        "Component_GradlePlugin_Performance_PerformanceLatestReleased",
        "Check_Gradleception",
        "Check_GradleceptionWithGroovy4"
    ) || isBuildCommitDistribution

val Project.isBuildCommitDistribution: Boolean
    get() = providers.gradleProperty("buildCommitDistribution").map { it.toBoolean() }.orElse(false).get()


fun Project.isInBuild(vararg buildTypeIds: String) = providers.environmentVariable("BUILD_TYPE_ID").orNull?.let { currentBuildTypeId ->
    buildTypeIds.any { currentBuildTypeId.endsWith(it) }
} ?: false
