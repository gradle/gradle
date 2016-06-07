/**
 * Small criterion like benchmarking framework.
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

private fun warmUp(config: BenchmarkConfig, experiment: () -> Unit) {
    for (i in 1..config.warmUpRuns) {
        experiment()
    }
}

private fun collectObservations(config: BenchmarkConfig, experiment: () -> Unit): List<Duration> =
    (1..config.observationRuns).map {
        durationOf(experiment)
    }
