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


/**
 * A small, criterion-like benchmarking framework.
 */
package criterion


fun benchmark(config: BenchmarkConfig, experiment: () -> Unit): BenchmarkResult {
    warmUp(config, experiment)
    val observations = collectObservations(config, experiment)
    return BenchmarkResult(observations)
}


data class BenchmarkConfig(val warmUpRuns: Int, val observationRuns: Int)


class BenchmarkResult(observations: List<Duration>) : Result<Duration>(observations) {

    override val Duration.magnitude: Double
        get() = ns

    override val Double.measure: Duration
        get() = Duration(this)
}


private
fun warmUp(config: BenchmarkConfig, experiment: () -> Unit) {
    for (i in 1..config.warmUpRuns) {
        experiment()
    }
}


private
fun collectObservations(config: BenchmarkConfig, experiment: () -> Unit): List<Duration> =
    (1..config.observationRuns).map {
        durationOf(experiment)
    }
