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

package gradlebuild.buildscan

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.ceil

abstract class CpuPerformanceCapturingService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    val performanceSamples = ConcurrentLinkedQueue<Int>()

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        if (OperatingSystem.current().isMacOsX) {
            val captureDataPoint = {
                val speedLimit = speedLimit()
                if (speedLimit != null) {
                    performanceSamples.add(speedLimit)
                }
            }
            scheduler.scheduleAtFixedRate(captureDataPoint, 0, 5, TimeUnit.SECONDS)
        } else {
            println("Not running on MacOS - no thermal throttling data will be captured")
        }
    }

    override fun close() {
        scheduler.shutdownNow()
    }

    fun getCpuPerformance() =
        if (performanceSamples.isEmpty()) null
        else {
            val samples = performanceSamples.toList().sorted() // ascending sort is intentional here as the lower the value the bigger actual throttling is
            CpuPerformance(
                samples.average().toInt(),
                samples.getOrElse(0) { -1 },
                samples.getOrElse(samples.size / 2) { -1 },
                listOf(50, 75, 95, 99).map { Percentile(it, percentile(samples, it)) }
            )
        }

    private fun percentile(samples: List<Int>, percentile: Int) =
        if (samples.size > 1) {
            val index = ceil(percentile / 100.0 * samples.size).toInt()
            samples[index - 1]
        } else -1

    private fun speedLimit(): Int? {
        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine("pmset", "-g", "therm")
            standardOutput = output
        }
        return if (result.exitValue == 0) parseOutput(output.toString()) else null
    }

    fun parseOutput(output: String) =
        output
            .lines()
            .find { it.trim().startsWith("CPU_Speed_Limit") }
            ?.split("=")
            ?.get(1)
            ?.trim()
            ?.toInt()

    data class ProcessResult(val exitCode: Int, val output: String)
    data class CpuPerformance(
        val average: Int,
        val max: Int,
        val median: Int,
        val percentiles: List<Percentile>
    )

    data class Percentile(val rank: Int, val value: Int)
}

fun addPerformanceMeasurement(buildScan: BuildScanExtension, performanceService: CpuPerformanceCapturingService) {
    buildScan.buildFinished {
        performanceService.getCpuPerformance()?.apply {
            buildScan.value("CPU Performance Average", average.toString())
            buildScan.value("CPU Performance Max", max.toString())
            buildScan.value("CPU Performance Median", median.toString())

            if (average < 100) {
                buildScan.tag("CPU_THROTTLED")
            }
        }
    }
}
