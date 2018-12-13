/*
 * Copyright 2018 the original author or authors.
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
import criterion.benchmark

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction


/**
 * Compares the execution time of two Kotlin expressions.
 *
 * Usage:
 *
 * ```
 * tasks.register<QuickBench>("quickBench") {
 *
 *     warmUpRuns = 100
 *
 *     observationRuns = 10000
 *
 *     baselineExperiment = {
 *         val schema = projectSchemaProviderOf(project).schemaFor(project)
 *         computeCacheKeyFor(schema)
 *     }
 *
 *     experiment = {
 *         val schema = projectSchemaProviderOf(project).schemaFor(project)
 *         optimalCacheKeyFor(schema)
 *     }
 * }
 * ```
 */
open class QuickBench : DefaultTask() {

    @Internal
    var baselineExperiment: () -> Unit = {}

    @Internal
    var experiment: () -> Unit = {}

    @Internal
    var warmUpRuns: Int = 3

    @Internal
    var observationRuns: Int = 11

    @TaskAction
    fun run() {

        val config = BenchmarkConfig(warmUpRuns, observationRuns)
        val baseline = benchmark(config, baselineExperiment)
        println("baseline: " + format(baseline))

        val latest = benchmark(config, experiment)
        println("latest: " + format(latest))

        val quotient = latest.median.ms / baseline.median.ms
        println(prettyPrint(quotient))

        reportBenchmarkResult(quotient)
    }
}
