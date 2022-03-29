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

import com.gradle.scan.plugin.BuildScanExtension
import java.util.concurrent.atomic.AtomicBoolean

val cacheMissTagged = AtomicBoolean(false)
plugins.withId("com.gradle.build-scan") {
    val buildScan = extensions.findByType<BuildScanExtension>()

    if (System.getenv("TEAMCITY_VERSION") != null) {
        gradle.taskGraph.afterTask {
            if (buildCacheEnabled() && isCacheMiss() && isNotTaggedYet()) {
                buildScan?.tag("CACHE_MISS")
            }
        }
    }
}

fun buildCacheEnabled() = gradle.startParameter.isBuildCacheEnabled

fun isNotTaggedYet() = cacheMissTagged.compareAndSet(false, true)

fun Task.isCacheMiss() = !state.skipped && state.failure == null && (isCompileCacheMiss() || isAsciidoctorCacheMiss())

fun Task.isCompileCacheMiss() = isMonitoredCompileTask() && !isExpectedCompileCacheMiss()

fun isAsciidoctorCacheMiss() = isMonitoredAsciidoctorTask() && !isExpectedAsciidoctorCacheMiss()

fun Task.isMonitoredCompileTask() = (this is AbstractCompile || this.isClasspathManifest()) && !this.isKotlinJsIrLink()

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
