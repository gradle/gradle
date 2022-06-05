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

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.support.serviceOf
import java.util.concurrent.atomic.AtomicBoolean

val gradleRootProject = when {
    project.name == "gradle" -> project.rootProject
    project.rootProject.name == "build-logic" -> rootProject.gradle.parent?.rootProject
    else -> project.gradle.parent?.rootProject
}

if (gradleRootProject != null && buildCacheEnabled() && System.getenv("TEAMCITY_VERSION") != null) {
    val rootProjectName = rootProject.name
    val isInBuildLogic = rootProjectName == "build-logic"
    gradle.taskGraph.whenReady {
        val cacheMissMonitorBuildService: Provider<CacheMissMonitorBuildService> = gradle.sharedServices.registerIfAbsent("cacheMissMonitorBuildService-$rootProjectName", CacheMissMonitorBuildService::class) {
            parameters.monitoredTaskPaths.set(allTasks.filter { it.isCacheMissMonitoredTask() }.map { if (isInBuildLogic) ":build-logic${it.path}" else it.path }.toSet())
        }
        gradle.serviceOf<BuildEventsListenerRegistry>().onTaskCompletion(cacheMissMonitorBuildService)

        gradleRootProject.extensions.extraProperties.set("cacheMiss-${rootProjectName}", cacheMissMonitorBuildService.get().cacheMiss)

        if (!isInBuildLogic) { // BuildScanExtension is only available in the gradle project
            val buildScan = gradleRootProject.extensions.findByType<BuildScanExtension>()
            val cacheMissInBuildLogic = gradleRootProject.extensions.extraProperties.get("cacheMiss-build-logic") as AtomicBoolean
            val cacheMissInGradle = gradleRootProject.extensions.extraProperties.get("cacheMiss-gradle") as AtomicBoolean
            buildScan?.buildFinished {
                if (cacheMissInGradle.get() || cacheMissInBuildLogic.get()) {
                    buildScan.tag("CACHE_MISS")
                }
            }
        }
    }
}

fun buildCacheEnabled() = gradle.startParameter.isBuildCacheEnabled

/**
 *  We monitor some tasks in non-seed builds. If a task executed in a non-seed build, we think it as "CACHE_MISS".
 */
fun Task.isCacheMissMonitoredTask() = isCompileCacheMissMonitoredTask() || isAsciidoctorCacheMissTask()

fun Task.isCompileCacheMissMonitoredTask() = isMonitoredCompileTask() && !isExpectedCompileCacheMiss()

fun isAsciidoctorCacheMissTask() = isMonitoredAsciidoctorTask() && !isExpectedAsciidoctorCacheMiss()

fun Task.isMonitoredCompileTask() = (this is AbstractCompile || this.isClasspathManifest()) && !isKotlinJsIrLink() && !isOnM1Mac()

// vendor is an input of GroovyCompile, so GroovyCompile on M1 mac is definitely a cache miss
fun Task.isOnM1Mac() = OperatingSystem.current().isMacOsX && System.getProperty("os.arch") == "aarch64"

fun Task.isClasspathManifest() = this.javaClass.simpleName.startsWith("ClasspathManifest")

// https://youtrack.jetbrains.com/issue/KT-49915
fun Task.isKotlinJsIrLink() = this.javaClass.simpleName.startsWith("KotlinJsIrLink")

fun isMonitoredAsciidoctorTask() = false // No asciidoctor tasks are cacheable for now

fun isExpectedAsciidoctorCacheMiss() =
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

fun isExpectedCompileCacheMiss() =
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
        "Check_Gradleception"
    ) || isBuildCommitDistribution

val Project.isBuildCommitDistribution: Boolean
    get() = providers.gradleProperty("buildCommitDistribution").map { it.toBoolean() }.orElse(false).get()


fun isInBuild(vararg buildTypeIds: String) = providers.environmentVariable("BUILD_TYPE_ID").orNull?.let { currentBuildTypeId ->
    buildTypeIds.any { currentBuildTypeId.endsWith(it) }
} ?: false
