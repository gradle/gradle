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

package org.gradle.kotlin.dsl.accessors


import java.lang.Math.sqrt
import java.lang.System.nanoTime


internal
fun benchmark(config: BenchmarkConfig, experiment: () -> Unit): BenchmarkResult {
    warmUp(config, experiment)
    val observations = collectObservations(config, experiment)
    return BenchmarkResult(observations)
}


internal
data class BenchmarkConfig(val warmUpRuns: Int, val observationRuns: Int)


internal
class BenchmarkResult(observations: List<Duration>) : Result<Duration>(observations) {

    override val Duration.magnitude: Double
        get() = ns

    override val Double.measure: Duration
        get() = Duration(this)
}


/**
 * A result of an experiment expressed as a set of observations measured in a unit [U].
 *
 * @param U the unit of measure
 */
internal
abstract class Result<U>(observations: List<U>) {

    init {
        assert(observations.isNotEmpty())
    }

    val points = observations.sortedBy { it.magnitude }

    val median by lazy {
        val middle = points.size / 2
        if (points.size.rem(2) == 0) {
            points.subList(middle - 1, middle + 1).average()
        } else {
            points[middle]
        }
    }

    val mean by lazy {
        points.average()
    }

    val stdDev by lazy {
        sqrt(variance).measure
    }

    val variance by lazy {
        points.map { square(it.magnitude - mean.magnitude) }.average()
    }

    abstract val U.magnitude: Double

    abstract val Double.measure: U

    fun Iterable<U>.average() =
        map { it.magnitude }.average().measure
}


private
fun square(d: Double) = d * d


internal
data class Duration(val ns: Double) {
    val ms: Double
        get() = ns / 1e+6
}


internal
inline fun durationOf(block: () -> Unit): Duration =
    nanoTimeOf(block).let {
        Duration(it.toDouble())
    }


internal
inline fun nanoTimeOf(block: () -> Unit): Long =
    nanoTime().let { start ->
        block()
        nanoTime() - start
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
