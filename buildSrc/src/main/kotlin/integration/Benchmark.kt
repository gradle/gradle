/*
 * Copyright 2016 the original author or authors.
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

package integration

import criterion.BenchmarkConfig
import criterion.BenchmarkResult
import criterion.Duration
import criterion.Result
import criterion.benchmark

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.GradleConnector.newConnector
import org.gradle.tooling.ProjectConnection

import java.io.ByteArrayOutputStream
import java.io.File

import java.lang.IllegalStateException

import java.util.Calendar
import java.util.TimeZone

open class Benchmark : DefaultTask() {

    var latestInstallation: File? = null

    var warmUpRuns = 7

    var observationRuns = 11

    var maxQuotient: Double = 1.10 // fails if becomes 10% slower

    var resultDir: File? = null

    @TaskAction
    fun run() {
        val config = BenchmarkConfig(warmUpRuns, observationRuns)
        val quotients = project.sampleDirs().filter { !it.name.contains("android") }.map {
            benchmark(it, config)
        }
        val result = QuotientResult(quotients)
        if (result.median > maxQuotient) {
            throw IllegalStateException(
                "Latest snapshot is around %.2f%% slower than baseline. Unacceptable!".format(
                    quotientToPercentage(result.median)))
        }
    }

    private fun quotientToPercentage(quotient: Double) = (quotient - 1) * 100

    private fun benchmark(sampleDir: File, config: BenchmarkConfig): Double {
        val relativeSampleDir = sampleDir.relativeTo(project.projectDir)
        println(relativeSampleDir)

        val baseline = benchmarkWith(connectorFor(sampleDir), config)
        println("\tbaseline: ${format(baseline)}")

        val latest = benchmarkWith(connectorFor(sampleDir).useInstallation(latestInstallation!!), config)
        println("\tlatest:   ${format(latest)}")

        val quotient = latest.median.ms / baseline.median.ms
        println("\tlatest / baseline: %.2f".format(quotient))

        appendToSampleResultFile(latest, sampleDir)
        return quotient
    }

    private fun benchmarkWith(connector: GradleConnector, config: BenchmarkConfig): BenchmarkResult =
        withUniqueDaemonRegistry {
            withConnectionFrom(connector) {
                benchmark(config) {
                    newBuild().forTasks("help").run()
                }
            }
        }

    private fun appendToSampleResultFile(result: BenchmarkResult, sampleDir: File) {
        resultFileFor(sampleDir)
            .apply { parentFile.mkdirs() }
            .appendText(toJsonLine(result))
    }

    private fun resultFileFor(sampleDir: File) =
        File(effectiveResultDir, "${sampleDir.name}.jsonl")

    private val effectiveResultDir by lazy {
        resultDir ?: File(project.buildDir, "benchmark")
    }

    private fun format(result: BenchmarkResult) =
        "%.2f ms    %.2f ms (std dev %.2f ms)".format(
            result.median.ms, result.mean.ms, result.stdDev.ms)

    private fun toJsonLine(result: BenchmarkResult) =
        """{"what": "%s", "when": "%s", "data": %s}${'\n'}""".format(
            commitHash, ISO8601Now, toJsonArray(result.points))

    private fun toJsonArray(points: List<Duration>) =
        points.joinToString(prefix = "[", postfix = "]", separator = ",") {
            "%.6f".format(it.ms)
        }

    private val ISO8601Now by lazy {
        // http://stackoverflow.com/a/11417382/214464
        javax.xml.bind.DatatypeConverter.printDateTime(Calendar.getInstance(TimeZone.getTimeZone("UTC")))
    }

    private val commitHash by lazy {
        println("Attempting to determine current commit hash via environment variable")
        System.getenv("BUILD_VCS_NUMBER").let { hash ->
            if (hash != null) {
                hash
            } else {
                println("Environment variable not present. Falling back to `git rev-parse`")
                val stdout = ByteArrayOutputStream()
                project.exec {
                    it.commandLine("git", "rev-parse", "HEAD")
                    it.standardOutput = stdout
                }
                String(stdout.toByteArray()).trim()
            }
        }
    }
}

class QuotientResult(observations: List<Double>) : Result<Double>(observations) {

    override val Double.magnitude: Double
        get() = this

    override val Double.measure: Double
        get() = this
}

fun connectorFor(projectDir: File) =
    newConnector().forProjectDirectory(projectDir)

inline fun <T> withConnectionFrom(connector: GradleConnector, block: ProjectConnection.() -> T): T =
    connector.connect().use(block)

inline fun <T> ProjectConnection.use(block: (ProjectConnection) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}

/**
 * Forces a new daemon process to be started by basing the registry on an unique temp dir.
 */
inline fun <T> withUniqueDaemonRegistry(block: () -> T) =
    withDaemonRegistry(createTempDir("gradle-script-kotlin-benchmark"), block)

inline fun <T> withDaemonRegistry(registryBase: File, block: () -> T) =
    withSystemProperty("org.gradle.daemon.registry.base", registryBase.path, block)

inline fun <T> withSystemProperty(key: String, value: String, block: () -> T): T {
    val originalValue = System.getProperty(key)
    try {
        System.setProperty(key, value)
        return block()
    } finally {
        setOrClearProperty(key, originalValue)
    }
}

fun setOrClearProperty(key: String, value: String?) {
    when (value) {
        null -> System.clearProperty(key)
        else -> System.setProperty(key, value)
    }
}
