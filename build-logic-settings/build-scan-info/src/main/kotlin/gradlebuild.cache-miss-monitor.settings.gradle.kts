/*
 * Copyright 2026 the original author or authors.
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

import gradlebuild.CacheMissMonitorBuildService
import gradlebuild.registerBuildScanInfoCollectingService

/**
 * Register a build service that monitors the compile tasks of the whole build tree
 * and reports CACHE_MISS if they're actually executed.
 */
if (gradle.startParameter.isBuildCacheEnabled && !isExpectedCompileCacheMiss()) {
    registerBuildScanInfoCollectingService(CacheMissMonitorBuildService::class.java) { cacheMissMonitor ->
        buildFinished {
            if (cacheMissMonitor.get().cacheMiss.get()) {
                tag("CACHE_MISS")
            }
        }
    }
}

fun isExpectedCompileCacheMiss() =
// Expected cache-miss:
// 1. CompileAll is the seed build
// 2. Gradleception which re-builds Gradle with a new Gradle version
// 3. buildScanPerformance test, which doesn't depend on compileAll
// 4. buildScanPerformance test, which doesn't depend on compileAll
// 5. Compile All for the experimental build cache NG
// 6. BuildCommitDistribution may build a commit which is not built before
    isInBuild(
        "Check_CompileAllBuild",
        "Component_GradlePlugin_Performance_PerformanceLatestMaster",
        "Component_GradlePlugin_Performance_PerformanceLatestReleased",
        "Check_Gradleception",
        "Check_GradleceptionWithJavaMaxLts",
        "Check_GradleceptionWithGroovy4",
        "CompileAllBuild_BuildCacheNG",
        "CompileAllBuild_NGRemote"
    ) || isBuildCommitDistribution()

fun isBuildCommitDistribution() = providers.gradleProperty("buildCommitDistribution").map { it.toBoolean() }.getOrElse(false)

fun isInBuild(vararg buildTypeIds: String) = providers.environmentVariable("BUILD_TYPE_ID").orNull?.let { currentBuildTypeId ->
    buildTypeIds.any { currentBuildTypeId.endsWith(it) }
} ?: false
