/*
 * Copyright 2020 the original author or authors.
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

package model

import common.Arch
import common.Os
import java.util.Locale

interface PerformanceTestBuildSpec {
    val type: PerformanceTestType
    val os: Os
    val arch: Arch
    val withoutDependencies: Boolean

    fun asConfigurationId(model: CIBuildModel, bucket: String): String
    fun channel(): String
}

interface PerformanceTestProjectSpec {
    val type: PerformanceTestType
    val failsStage: Boolean

    fun asConfigurationId(model: CIBuildModel): String
    fun asName(): String
    fun channel(): String
}

data class PerformanceTestPartialTrigger(
    val triggerName: String,
    val triggerId: String,
    val dependencies: List<PerformanceTestCoverage>
)

data class PerformanceTestCoverage(
    private val uuid: Int,
    override val type: PerformanceTestType,
    override val os: Os,
    override val arch: Arch = Arch.AMD64,
    val numberOfBuckets: Int = 40,
    private val oldUuid: String? = null,
    override val withoutDependencies: Boolean = false,
    override val failsStage: Boolean = true
) : PerformanceTestBuildSpec, PerformanceTestProjectSpec {
    override
    fun asConfigurationId(model: CIBuildModel, bucket: String) =
        "${asConfigurationId(model)}$bucket"

    override
    fun asConfigurationId(model: CIBuildModel) =
        "${model.projectId}_${oldUuid ?: "PerformanceTest$uuid"}"

    override
    fun asName(): String =
        "${type.displayName} - ${os.asName()}${if (withoutDependencies) " without dependencies" else ""}"

    override
    fun channel() =
        "${type.channel}${if (os == Os.LINUX) "" else "-${os.name.lowercase(Locale.US)}"}-%teamcity.build.branch%"
}

data class FlameGraphGeneration(
    private val uuid: Int,
    private val name: String,
    private val scenarios: List<PerformanceScenario>
) : PerformanceTestProjectSpec {
    override
    fun asConfigurationId(model: CIBuildModel) =
        "${model.projectId}_PerformanceTest$uuid"

    override
    fun asName(): String =
        "Flamegraphs for $name"

    override
    fun channel(): String = "adhoc-%teamcity.build.branch%"

    override
    val type: PerformanceTestType
        get() = PerformanceTestType.adHoc

    override
    val failsStage: Boolean
        get() = false

    val buildSpecs: List<FlameGraphGenerationBuildSpec>
        get() = scenarios.flatMap { scenario ->
            Os.values().flatMap { os ->
                val arch = if (os == Os.MACOS) Arch.AARCH64 else Arch.AMD64
                if (os == Os.WINDOWS) {
                    listOf("jprofiler")
                } else {
                    listOf("async-profiler", "async-profiler-heap")
                }.map { FlameGraphGenerationBuildSpec(scenario, os, arch, it) }
            }
        }

    inner
    class FlameGraphGenerationBuildSpec(
        val performanceScenario: PerformanceScenario,
        override val os: Os,
        override val arch: Arch,
        val profiler: String
    ) : PerformanceTestBuildSpec {
        override
        val type: PerformanceTestType = PerformanceTestType.adHoc

        override
        val withoutDependencies: Boolean = true

        override
        fun asConfigurationId(model: CIBuildModel, bucket: String): String =
            "${this@FlameGraphGeneration.asConfigurationId(model)}$bucket"

        override
        fun channel(): String = this@FlameGraphGeneration.channel()
    }
}
